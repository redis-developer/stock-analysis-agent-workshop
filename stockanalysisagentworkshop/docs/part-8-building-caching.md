# Part 8: Building Caching

In this part, you will build the two cache layers that make this multi-agent system faster and cheaper:

- the regular cache layer for external data
- the semantic cache layer for reusable final answers

You should only work on these files:

- `src/main/java/com/redis/stockanalysisagent/cache/CacheConfig.java`
- `src/main/java/com/redis/stockanalysisagent/cache/ExternalDataCache.java`
- `src/main/java/com/redis/stockanalysisagent/semanticcache/SemanticAnalysisCache.java`

The providers and chat flow are already wired to use these caches.

That means:

- the market-data, SEC, news, and technical providers already call `ExternalDataCache`
- the chat layer already checks `SemanticAnalysisCache` before running the full pipeline

Your job in this part is to make those cache layers real.

## What You Are Building

By the end of this part, the system should be able to:

1. store provider results in Redis with the right TTL per data type
2. reuse exact cached provider results instead of calling the provider again
3. check whether a new user request is close enough to a prior request
4. reuse a cached final answer when semantic similarity is high enough

That is what makes the system more scalable and more cost effective.

## Before You Start: The Two Cache Layers

This part only makes sense if you keep the two cache layers separate in your head.

They sit at different parts of the request path.

### Regular Cache

Regular cache sits around provider and data access.

It answers:

"Have we already fetched this exact data recently?"

That is useful for:

- quote snapshots
- technical-analysis snapshots
- SEC reference data
- Tavily news search results

### Semantic Cache

Semantic cache sits at the chat boundary.

It answers:

"Have we already answered a very similar user request recently?"

That is useful for:

- rephrased requests
- near-duplicate stock-analysis questions
- avoiding a full coordinator + agents + synthesis run

## Step 1: Implement the Redis Cache Manager

Open:

`src/main/java/com/redis/stockanalysisagent/cache/CacheConfig.java`

Find this method:

```java
@Bean
@ConditionalOnProperty(prefix = "spring.cache", name = "type", havingValue = "redis")
public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
    // PART 8 STEP 1:
    // Replace this method body with the snippet from the Part 8 guide.
    return null;
}
```

Replace the method body with this exact code:

```java
GenericJacksonJsonRedisSerializer valueSerializer = new GenericJacksonJsonRedisSerializer(
        JsonMapper.builder()
                .findAndAddModules()
                .build()
);

RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
        .disableCachingNullValues()
        .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
        .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer))
        .entryTtl(Duration.ofMinutes(10));

return RedisCacheManager.builder(connectionFactory)
        .cacheDefaults(defaults)
        .withInitialCacheConfigurations(Map.of(
                CacheNames.MARKET_DATA_QUOTES, defaults.entryTtl(Duration.ofSeconds(30)),
                CacheNames.TECHNICAL_ANALYSIS_SNAPSHOTS, defaults.entryTtl(Duration.ofMinutes(2)),
                CacheNames.SEC_TICKER_INDEX, defaults.entryTtl(Duration.ofHours(24)),
                CacheNames.SEC_COMPANY_FACTS, defaults.entryTtl(Duration.ofHours(12)),
                CacheNames.SEC_SUBMISSIONS, defaults.entryTtl(Duration.ofMinutes(15)),
                CacheNames.TAVILY_NEWS_SEARCH, defaults.entryTtl(Duration.ofHours(24))
        ))
        .build();
```

Why you did this:

- different kinds of data in this app have different freshness requirements
- provider caches need Redis-backed storage and explicit TTLs
- the cache manager is the place where the application defines those boundaries

What this code is doing:

- it creates the Redis-backed cache manager used by the regular cache layer
- it configures JSON serialization so cached objects can be stored and reloaded safely
- it defines a default TTL and then overrides it for the app's important cache regions
- it gives quote data, technical data, SEC data, and news data different reuse windows

## Step 2: Implement `getOrLoad(...)`

Open:

`src/main/java/com/redis/stockanalysisagent/cache/ExternalDataCache.java`

Find this method:

```java
@SuppressWarnings("unchecked")
public <T> T getOrLoad(String cacheName, String key, Supplier<T> loader) {
    // PART 8 STEP 2:
    // Replace this method body with the snippet from the Part 8 guide.
    return loader.get();
}
```

Replace the method body with this exact code:

```java
Cache cache = cacheManager.getCache(cacheName);
if (cache == null) {
    return loader.get();
}

Cache.ValueWrapper cached = cache.get(key);
if (cached != null && cached.get() != null) {
    return (T) cached.get();
}

String lockKey = cacheName + "::" + key;
Object lock = locks.computeIfAbsent(lockKey, ignored -> new Object());
synchronized (lock) {
    try {
        Cache.ValueWrapper doubleChecked = cache.get(key);
        if (doubleChecked != null && doubleChecked.get() != null) {
            return (T) doubleChecked.get();
        }

        T loaded = loader.get();
        if (loaded != null) {
            cache.put(key, loaded);
        }
        return loaded;
    } finally {
        locks.remove(lockKey, lock);
    }
}
```

Why you did this:

- the providers need one small wrapper that handles cache hit, cache miss, and cache store logic consistently
- without that wrapper, each provider would have to duplicate its own cache flow
- the lock prevents several identical requests from stampeding the provider at the same time

What this code is doing:

- it looks up an exact cache key in the chosen cache region
- it returns the cached value immediately on a hit
- it double-checks inside a lock on a miss so concurrent requests do not all load the same data at once
- it calls the real provider only when the value is not already cached
- it stores the loaded value so future requests can reuse it

## Step 3: Implement `findAnswer(...)`

Open:

`src/main/java/com/redis/stockanalysisagent/semanticcache/SemanticAnalysisCache.java`

Find this method:

```java
public Optional<String> findAnswer(String request) {
    // PART 8 STEP 3:
    // Replace this method body with the snippet from the Part 8 guide.
    return Optional.empty();
}
```

Replace the method body with this exact code:

```java
return semanticCache.check(request)
        .map(cacheHit -> {
            log.info("Semantic cache hit for request at distance {}", cacheHit.getDistance());
            return cacheHit.getResponse();
        });
```

Why you did this:

- the chat layer needs one method that answers a simple question:
  "Do we already have a reusable final answer for this request?"
- the semantic cache service hides the embedding and similarity logic behind that simple interface

What this code is doing:

- it asks the semantic cache to compare the current request to recently stored requests
- it returns a cached final answer only when the similarity is close enough
- it logs the hit distance so the application can observe how strict or loose the cache is behaving

## Step 4: Implement `store(...)`

In the same file, find this method:

```java
public void store(String request, String response) {
    // PART 8 STEP 4:
    // Replace this method body with the snippet from the Part 8 guide.
}
```

Replace the method body with this exact code:

```java
semanticCache.store(
        request,
        response,
        Map.of("kind", "stock-analysis")
);
log.info("Semantic cache stored response for request.");
```

Why you did this:

- once the full pipeline produces a reusable final answer, the app needs to persist it for later semantic reuse
- the metadata keeps the semantic cache scoped to this application's answer type

What this code is doing:

- it stores the final answer together with the original request in the semantic cache
- it tags the entry as stock-analysis content
- it makes that answer available for later meaning-based reuse

## Step 5: Implement `initializeCache(...)`

In the same file, find this method:

```java
private SemanticCache initializeCache(
        SemanticCacheProperties properties,
        EmbeddingModel embeddingModel,
        String redisHost,
        int redisPort
) {
    // PART 8 STEP 5:
    // Replace this method body with the snippet from the Part 8 guide.
    return null;
}
```

Replace the method body with this exact code:

```java
UnifiedJedis redisClient = new UnifiedJedis("redis://%s:%s".formatted(redisHost, redisPort));
return new SemanticCache.Builder()
        .name(properties.getName())
        .redisClient(redisClient)
        .vectorizer(new SpringAiEmbeddingVectorizer(
                properties.getEmbeddingModelName(),
                embeddingModel,
                properties.getEmbeddingDimensions()
        ))
        .distanceThreshold(properties.getDistanceThreshold())
        .ttl(properties.getTtlSeconds())
        .build();
```

Why you did this:

- semantic caching needs Redis storage, an embedding model, and a similarity threshold
- this class is where those pieces are assembled into one reusable cache service

What this code is doing:

- it creates the Redis client used by the semantic cache
- it tells the semantic cache which embedding model and vector dimensions to use
- it sets the similarity threshold that decides whether a cached answer is close enough to reuse
- it sets the semantic cache TTL so reused answers stay fresh

## Why This Design Fits a Production-Ready Multi-Agent System

You are not using one cache for everything.

You are using:

- regular cache for deterministic provider work
- semantic cache for reusable final answers

That matters because the app has two different cost surfaces:

- external data fan-out
- full pipeline execution

This design reduces both:

- provider calls get reused through `ExternalDataCache`
- whole near-duplicate requests get reused through `SemanticAnalysisCache`

That is what makes the system more scalable without changing the agent architecture itself.

## What “Done” Looks Like

You are done when you understand this flow:

1. a provider asks `ExternalDataCache` for a value
2. an exact cache hit skips the provider call
3. a miss loads the value once and stores it in Redis
4. the chat layer checks `SemanticAnalysisCache` before running the full agent pipeline
5. a semantic hit reuses a recent final answer
6. a semantic miss runs the pipeline and then stores the final answer for later reuse

At that point, your multi-agent system is not only correct.

It is also much more efficient.
