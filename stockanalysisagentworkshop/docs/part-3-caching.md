# Part 3: Regular Caching and Semantic Caching

## Learning Goal

Teach learners how to reduce repeated work at two different layers:

- deterministic data caching
- semantic answer reuse

## New Concepts

- Redis-backed provider caching
- TTLs
- cache boundaries
- semantic cache placement before orchestration
- safe vs unsafe reuse

## What Changes From Part 2

Keep:

- the same agents
- the same orchestration
- the same memory path

Add:

- provider cache layer
- semantic cache layer

## What Learners Should Explicitly Implement

Learners should write:

- cache integration for deterministic provider calls
- semantic cache lookup and store flow
- semantic-cache boundary in the chat or request entrypoint

## What Should Be Pre-Provided

Pre-provide:

- cache configuration shells
- semantic cache property classes
- RedisVL dependency setup
- placeholder embedding or vectorizer support if needed

## Recommended New Package Structure

```text
cache/
  CacheConfig.java
  CacheNames.java
  ExternalDataCache.java
semanticcache/
  SemanticAnalysisCache.java
  SemanticCacheProperties.java
```

## Key Teaching Point

Learners should understand that:

- provider caching avoids repeating deterministic external fetches
- semantic caching avoids repeating whole analysis work
- these are not the same thing

## Validation

- repeat the same deterministic request twice
- repeat a semantically similar request

Expected:

- provider cache reduces repeated tool fetches
- semantic cache can short-circuit repeated analysis
