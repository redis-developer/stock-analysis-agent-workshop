# Part 8: Building Caching

In this part, you will build the two cache layers that make this multi-agent system faster and cheaper:

- the regular cache layer for external data
- the semantic cache layer for reusable final answers

You should only work on these files:

- `src/main/java/com/redis/stockanalysisagent/cache/CacheConfig.java`
- `src/main/java/com/redis/stockanalysisagent/cache/ExternalDataCache.java`
- `src/main/java/com/redis/stockanalysisagent/semanticcache/SemanticAnalysisCache.java`
- `src/main/java/com/redis/stockanalysisagent/semanticcache/SemanticCacheAdvisor.java`
- `src/main/java/com/redis/stockanalysisagent/semanticcache/SemanticCacheConfig.java`
- `src/main/java/com/redis/stockanalysisagent/agent/coordinatoragent/CoordinatorRoutingAgentConfig.java`
- `src/main/java/com/redis/stockanalysisagent/agent/coordinatoragent/CoordinatorRoutingAgent.java`
- `src/main/java/com/redis/stockanalysisagent/chat/ChatAnalysisService.java`

The provider call sites are already wired to use these caches.

That means:

- the market-data, SEC, news, and technical providers already call `ExternalDataCache`
- the workshop code already contains TODO markers for advisor-based semantic caching before coordinator routing and long-term memory retrieval

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

Semantic cache sits in an advisor before the coordinator runs.

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

## Step 3: Implement `findResponse(...)`

Open:

`src/main/java/com/redis/stockanalysisagent/semanticcache/SemanticAnalysisCache.java`

Find this method:

```java
public Optional<String> findResponse(String request) {
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

- the advisor needs one method that answers a simple question:
  "Do we already have a reusable final answer for this request?"
- the semantic cache service hides the embedding and similarity logic behind that simple interface

What this code is doing:

- it asks the semantic cache to compare the current request to recently stored requests
- it returns a cached final answer only when the similarity is close enough
- it logs the hit distance so the application can observe how strict or loose the cache is behaving
- the existing `findAnswer(...)` wrapper can keep delegating to `findResponse(...)`

## Step 4: Implement `storeResponse(...)`

In the same file, find this method:

```java
public void storeResponse(String request, String response) {
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
- the existing `store(...)` wrapper can keep delegating to `storeResponse(...)`

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
SearchIndex index = createIndex(properties, redisClient);
ensureIndexExists(index, properties.getName());

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

Then add these helper methods below `initializeCache(...)`:

```java
private SearchIndex createIndex(SemanticCacheProperties properties, UnifiedJedis redisClient) {
    IndexSchema schema = IndexSchema.builder()
            .name(properties.getName())
            .prefix("cache:" + properties.getName() + ":")
            .storageType(IndexSchema.StorageType.HASH)
            .addTextField("prompt", field -> {
            })
            .addTextField("response", field -> {
            })
            .addVectorField("prompt_vector", properties.getEmbeddingDimensions(), field -> field
                    .algorithm(redis.clients.jedis.search.schemafields.VectorField.VectorAlgorithm.FLAT)
                    .distanceMetric(VectorField.DistanceMetric.COSINE))
            .addNumericField("inserted_at", field -> {
            })
            .addNumericField("updated_at", field -> {
            })
            .addTagField("user", field -> {
            })
            .addTagField("session", field -> {
            })
            .addTagField("category", field -> {
            })
            .build();

    return new SearchIndex(schema, redisClient);
}

private void ensureIndexExists(SearchIndex index, String cacheName) {
    if (index.exists()) {
        return;
    }

    index.create();

    if (!index.exists()) {
        throw new IllegalStateException("Semantic cache index %s was not created.".formatted(cacheName));
    }

    log.info("Created semantic cache index {}", cacheName);
}
```

Add these imports if they are not already present:

```java
import com.redis.vl.schema.IndexSchema;
import com.redis.vl.schema.VectorField;
```

Why you did this:

- semantic caching needs Redis storage, an embedding model, and a similarity threshold
- this class is where those pieces are assembled into one reusable cache service
- the semantic cache index has to exist before the first lookup or store can work

What this code is doing:

- it creates the Redis client used by the semantic cache
- it creates the Redis search index definition used by the semantic cache
- it creates that index the first time the app starts if it is missing
- it tells the semantic cache which embedding model and vector dimensions to use
- it sets the similarity threshold that decides whether a cached answer is close enough to reuse
- it sets the semantic cache TTL so reused answers stay fresh

## Step 6: Implement the Semantic Cache Advisor

Open:

`src/main/java/com/redis/stockanalysisagent/semanticcache/SemanticCacheAdvisor.java`

Find this method:

```java
@Override
public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
    // PART 8 STEP 6:
    // Replace this method body with the advisor-based semantic cache snippet from the Part 8 guide.
    // The finished advisor should:
    // 1. bypass semantic lookup when BYPASS_CACHE is true
    // 2. normalize the current user message into a cache key
    // 3. short circuit on cache hit with a synthetic DIRECT_RESPONSE payload
    // 4. mark the response context with CACHE_HIT
    return chain.nextCall(request).mutate()
            .context(CACHE_HIT, false)
            .build();
}
```

Replace the method body with this exact code:

```java
if (Boolean.TRUE.equals(request.context().get(BYPASS_CACHE))) {
    return chain.nextCall(request);
}

String cacheKey = cacheKey(request);
if (cacheKey == null) {
    return chain.nextCall(request);
}

var cachedResponse = semanticCache.findResponse(cacheKey);
if (cachedResponse.isPresent()) {
    return ChatClientResponse.builder()
            .chatResponse(toChatResponse(cachedResponse.get()))
            .context(request.context())
            .context(CACHE_HIT, true)
            .build();
}

return chain.nextCall(request).mutate()
        .context(CACHE_HIT, false)
        .build();
```

Then add these imports:

```java
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import tools.jackson.core.io.JsonStringEncoder;

import java.util.List;
```

Add this field below `DEFAULT_ORDER`:

```java
private static final JsonStringEncoder JSON_STRING_ENCODER = JsonStringEncoder.getInstance();
```

Then add these helper methods below `adviseCall(...)`:

```java
private String cacheKey(ChatClientRequest request) {
    if (request == null || request.prompt() == null || request.prompt().getUserMessage() == null) {
        return null;
    }

    String text = request.prompt().getUserMessage().getText();
    if (text == null) {
        return null;
    }

    String normalized = text.trim();
    return normalized.isEmpty() ? null : normalized;
}

private ChatResponse toChatResponse(String finalResponse) {
    return ChatResponse.builder()
            .metadata(ChatResponseMetadata.builder()
                    .keyValue(CACHE_HIT, true)
                    .build())
            .generations(List.of(new Generation(new AssistantMessage(toCoordinatorPayload(finalResponse)))))
            .build();
}

private String toCoordinatorPayload(String finalResponse) {
    StringBuilder escapedFinalResponse = new StringBuilder();
    JSON_STRING_ENCODER.quoteAsString(finalResponse == null ? "" : finalResponse, escapedFinalResponse);
    return "{\"finishReason\":\"DIRECT_RESPONSE\",\"finalResponse\":\"%s\"}".formatted(escapedFinalResponse);
}
```

Why you did this:

- advisor-based semantic caching should short circuit before long-term memory retrieval and coordinator routing
- the advisor converts a cached final answer into the smallest valid coordinator payload
- the `CACHE_HIT` marker gives the rest of the application a clean way to observe the hit or miss result

What this code is doing:

- it checks the semantic cache before the coordinator call continues
- it treats the current user message as the cache lookup key
- it returns a synthetic `DIRECT_RESPONSE` payload on cache hit
- it lets the normal coordinator flow continue on cache miss

## Step 7: Register the Semantic Cache Advisor Bean

Open:

`src/main/java/com/redis/stockanalysisagent/semanticcache/SemanticCacheConfig.java`

Find this method:

```java
@Bean
public SemanticCacheAdvisor semanticCacheAdvisor(SemanticAnalysisCache semanticAnalysisCache) {
    // PART 8 STEP 7:
    // Keep the semantic cache advisor bean in the semantic-cache package so the
    // coordinator config can inject it without constructing it inline.
    return new SemanticCacheAdvisor(semanticAnalysisCache);
}
```

Keep the return statement as shown.

Why you did this:

- semantic cache wiring belongs with the semantic cache package, not inside the coordinator package
- the coordinator config should depend on the advisor bean, not construct it inline

## Step 8: Wire the Advisor on the Coordinator `ChatClient`

Open:

`src/main/java/com/redis/stockanalysisagent/agent/coordinatoragent/CoordinatorRoutingAgentConfig.java`

After you complete the Part 4 coordinator client bean, update the method signature so it receives `SemanticCacheAdvisor semanticCacheAdvisor`.

Then make the bean return this:

```java
return ChatClient.builder(chatModel)
        .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
        .defaultAdvisors(semanticCacheAdvisor)
        .defaultAdvisors(longTermMemoryAdvisor)
        .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
        .defaultSystem(DEFAULT_PROMPT)
        .build();
```

Why you did this:

- semantic caching should happen before long-term memory retrieval
- the coordinator still needs native structured output so the routing decision is parsed as `RoutingDecision`

What this code is doing:

- it runs working memory first
- then semantic cache
- then long-term memory
- then the coordinator model call

## Step 9: Surface Semantic Cache Hits in `CoordinatorRoutingAgent`

Open:

`src/main/java/com/redis/stockanalysisagent/agent/coordinatoragent/CoordinatorRoutingAgent.java`

Replace `route(...)` with this:

```java
public RoutingResult route(String userMessage, String conversationId) {
    ResponseEntity<ChatResponse, RoutingDecision> response = coordinatorChatClient
            .prompt()
            .user(userMessage)
            .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
            .call()
            .responseEntity(RoutingDecision.class);

    RoutingDecision decision = response.entity();
    if (decision == null) {
        throw new IllegalStateException("Coordinator returned no routing decision.");
    }
    ChatResponse chatResponse = response.response();
    boolean fromSemanticCache = chatResponse != null
            && Boolean.TRUE.equals(chatResponse.getMetadata().getOrDefault(SemanticCacheAdvisor.CACHE_HIT, false));

    return new RoutingResult(decision, TokenUsageSummary.from(chatResponse), fromSemanticCache);
}
```

Make sure the record now includes the boolean:

```java
public record RoutingResult(
        RoutingDecision routingDecision,
        TokenUsageSummary tokenUsage,
        boolean fromSemanticCache
) {
}
```

Add these imports:

```java
import com.redis.stockanalysisagent.semanticcache.SemanticCacheAdvisor;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
```

If your `CoordinatorAgent.RoutingOutcome` still has only two fields, extend it so it also carries `fromSemanticCache` and pass the flag through in `execute(...)`.

Why you did this:

- the advisor context is not exposed directly once you consume a typed `responseEntity(...)`
- the cache hit flag needs to survive so the UI and observability layers can see it

## Step 10: Store Final Answers After a Completed Miss

Open:

`src/main/java/com/redis/stockanalysisagent/chat/ChatAnalysisService.java`

Find this commented line in `analyze(...)`:

```java
// semanticAnalysisCache.storeResponse(request, response.answer());
```

Uncomment it so the code becomes:

```java
semanticAnalysisCache.storeResponse(request, response.answer());
```

Why you did this:

- the advisor can only reuse answers that were stored after previous completed runs
- storing after the full pipeline finishes keeps semantic caching scoped to complete final answers

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
4. the semantic cache advisor checks `SemanticAnalysisCache` before coordinator routing and long-term memory retrieval
5. a semantic hit returns a synthetic `DIRECT_RESPONSE` payload through the coordinator contract
6. a semantic miss runs the full pipeline and then stores the final answer for later reuse

At that point, your multi-agent system is not only correct.

It is also much more efficient.
