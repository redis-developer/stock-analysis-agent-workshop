# Part 7: Scaling with Caching

At this point, your multi-agent system can:

- route requests
- run specialist agents
- preserve memory across turns

That is enough to make the system useful.

It is not enough to make the system fast, affordable, and scalable.

As usage grows, two new problems appear:

- the app repeats the same expensive work too often
- the app spends model and provider cost on requests it has effectively answered before

This is where caching enters the architecture.

In this part, you will learn the two cache layers used in this application:

- regular caching
- semantic caching

They solve different problems.

## Why Caching Matters in This Application

This stock-analysis system does not answer a user question in one cheap step.

A single request may trigger:

- routing
- market-data provider calls
- SEC data lookups
- news provider calls
- technical-analysis provider calls
- synthesis

That means one user message can fan out into multiple downstream requests.

If we repeat all of that work every time, the system becomes:

- slower
- more expensive
- more likely to hit provider rate limits

Caching lets us reuse work that does not need to be recomputed yet.

That is how we make the system more production-ready.

## 1. What Regular Caching Is

Regular caching means:

- the application stores the result of a computation or provider call
- the next identical request reuses that stored result
- the cache entry expires after a chosen amount of time

This is exact-match reuse.

If the key matches, the app reuses the cached value.

If the key does not match, the app does the work again.

## Where Regular Caching Fits in This App

In this application, regular caching happens around external data access.

That is the right place for it because provider calls are:

- repeated often
- relatively expensive compared to a cache read
- sometimes rate-limited
- often stable for at least a short period of time

The main cache wrapper is `ExternalDataCache`.

It sits between the providers and Redis-backed Spring caching.

Conceptually, the flow looks like this:

```text
Provider asks for data
    |
    v
ExternalDataCache
    |
    +--> cache hit -> return cached value
    |
    +--> cache miss -> call provider -> store result -> return result
```

## Real Examples from This Application

This is not theoretical.

The current app already uses regular caching in several provider paths.

### Example 1: Market data quotes

`TwelveDataMarketDataProvider` caches quote snapshots in:

- `market-data-quotes`

That cache uses a short TTL because quote data changes quickly.

In this application, the TTL is:

- 30 seconds

Why that helps:

- if several users ask for Apple within a short window, the app does not keep calling the market-data provider for the same quote
- if the same conversation asks follow-up questions about the same ticker, the app can reuse the recent quote snapshot

### Example 2: Technical analysis snapshots

`TwelveDataTechnicalAnalysisProvider` caches indicator snapshots in:

- `technical-analysis-snapshots`

That cache uses a slightly longer TTL:

- 2 minutes

Why that helps:

- technical indicators are derived from provider data and do not need to be recalculated on every identical request
- repeated chart-oriented questions can reuse the same recent technical snapshot

### Example 3: SEC ticker index

`SecTickerLookupService` caches the SEC ticker index in:

- `sec-ticker-index`

That cache uses a much longer TTL:

- 24 hours

Why that helps:

- the SEC ticker mapping does not need to be fetched on every request
- this is exactly the kind of stable reference data that should be cached aggressively

### Example 4: SEC company facts and submissions

The app also caches:

- `sec-company-facts`
- `sec-submissions`

Those caches are used by the fundamentals and news flows.

Their TTLs are longer than quote data, because those datasets change less frequently than intraday market prices.

### Example 5: Tavily news search

`TavilyNewsProvider` caches news search results in:

- `tavily-news-search`

That cache uses a long TTL:

- 24 hours

Why that helps:

- many users ask similar news questions about the same company
- repeated news searches can be expensive and unnecessary

## Why This Makes the System Cheaper

Regular caching reduces cost in a very direct way.

It cuts down on repeated:

- HTTP calls
- provider usage
- latency
- downstream fan-out

In this app, that means fewer repeated calls to:

- Twelve Data
- SEC endpoints
- Tavily

And that means:

- lower provider cost
- lower risk of throttling
- faster specialist-agent execution

## Why TTLs Matter

Every cache entry needs an expiration policy.

That is because not all data has the same freshness requirements.

In this app:

- quote data gets a short TTL
- technical snapshots get a short-to-medium TTL
- SEC reference data gets a long TTL
- news search results get a longer TTL

This is an important production lesson:

Do not choose one TTL for everything.

Choose TTLs based on how quickly the underlying data changes and how wrong a stale answer would be.

## 2. What Semantic Caching Is

Semantic caching solves a different problem.

Regular caching works only when the key matches exactly.

But users often ask the same thing in slightly different ways:

- "Give me a quick analysis of Apple."
- "Can you analyze AAPL for me?"
- "What do you think about Apple stock right now?"

Those are not the same exact string.

But they may be close enough that re-running the full pipeline is wasteful.

Semantic caching stores a response together with an embedding of the request.

Later, when a new request arrives, the app:

- embeds the new request
- compares it to cached requests
- checks whether the distance is close enough
- reuses the cached response if it is sufficiently similar

So semantic caching is:

- meaning-based reuse
- not string-based reuse

## Where Semantic Caching Fits in This App

In this application, semantic caching sits in an advisor before coordinator routing and long-term memory retrieval.

It gives the app an early exit for near-duplicate requests.

Conceptually, the flow looks like this:

```text
User message
    |
    v
Semantic cache advisor
    |
    +--> hit -> return cached final answer and stop
    |
    +--> miss -> run long-term memory retrieval, then coordinator + agents + synthesis
                     |
                     v
                 store final answer in semantic cache
```

That means semantic caching is not caching one provider call.

It is caching the final response to a user request.

## The Semantic Cache in This App

The application uses `SemanticAnalysisCache`.

It:

- stores final answers in Redis
- uses embeddings to compare new requests with previous ones
- uses a distance threshold to decide whether a cached answer is similar enough to reuse

In the current configuration, the semantic cache has:

- name: `stock-analysis-semantic-cache`
- embedding model: `text-embedding-3-small`
- embedding dimensions: `1536`
- TTL: `300` seconds
- distance threshold: `0.12`

Those settings reflect an important idea:

Semantic cache entries should usually be:

- fairly fresh
- fairly strict

because reusing a wrong answer is worse than missing a cache hit.

## Why Semantic Caching Helps Here

Semantic caching is useful in this app because many user requests are near-duplicates.

Examples:

- repeated company analysis questions
- slight rephrasings of the same stock request
- multiple users asking similar broad questions about the same ticker

Without semantic caching, each one of those requests would still trigger:

- long-term memory retrieval
- coordinator
- specialist agents
- synthesis

With semantic caching, the app can sometimes skip all of that and return a reusable final answer immediately.

That saves:

- model tokens
- provider calls
- orchestration time
- total end-to-end latency

## Regular Cache vs Semantic Cache

These two caches do not compete.

They operate at different layers.

### Regular cache

Regular cache is best for:

- external data
- exact-match lookups
- deterministic provider responses

In this app, that includes:

- quotes
- technical snapshots
- SEC data
- Tavily search results

### Semantic cache

Semantic cache is best for:

- final answers
- natural-language request reuse
- meaning-level similarity

In this app, that means:

- reusing a final stock-analysis answer when a new request is close enough to a recent prior request

## Why We Need Both

If you only use regular caching:

- you still pay full orchestration and model cost for rephrased requests

If you only use semantic caching:

- you still repeat many provider calls inside the pipeline on every semantic-cache miss

We need both because they reduce cost at different layers:

- regular cache reduces provider and data-fetch cost
- semantic cache reduces full pipeline and model cost

That is what makes the system both faster and cheaper.

## The Full Cache-Aware Flow

Once both layers exist, the request path looks like this:

```text
User message
    |
    v
Semantic cache advisor
    |
    +--> hit -> return final answer
    |
    +--> miss
            |
            v
        Long-term memory advisor
            |
            v
        Coordinator
            |
            v
        Orchestrator
            |
            +--> Specialist agents
                    |
                    +--> Providers use regular cache
            |
            v
        Synthesis
            |
            v
        Store final answer in semantic cache
```

This is the key production lesson:

Regular caching and semantic caching belong at different layers of the system.

## Cache Safety and Boundaries

Caching only works when you are clear about what is safe to reuse.

That is especially important in a memory-aware system.

### Regular caching boundary

Regular cache should wrap:

- provider calls
- deterministic transformations
- data that can tolerate short-term reuse

It should not be used carelessly for:

- highly personalized final responses
- logic that depends heavily on rapidly changing hidden context

### Semantic caching boundary

Semantic cache should be used only when the final answer is genuinely reusable.

That means you should think carefully about:

- freshness
- personalization
- time sensitivity
- user-specific memory context

In a production system, semantic caching must be conservative.

A missed cache hit is cheaper than a wrong reused answer.

## What You Should Take Away from This Part

Before you implement the caching layers, you should leave this part with four ideas:

1. regular caching and semantic caching solve different problems
2. regular caching belongs around provider and data access
3. semantic caching belongs at the request boundary, before the full pipeline runs
4. production-ready caching is about reuse with boundaries, not reuse at all costs

In the next part, you will wire these caching layers into the application.
