# Workshop Checkpoints

Last updated: 2026-03-16

Use this file as the practical delivery map for the workshop. Each checkpoint describes:

- what learners should implement
- what the facilitator can provide up front
- how to validate the slice before moving on

## Checkpoint 1: Orchestration Foundation

### Learners Implement

- the core orchestration types such as `AgentType`, `ExecutionPlan`, and `AgentExecution`
- the coordinator flow with `COMPLETED`, `NEEDS_MORE_INPUT`, `OUT_OF_SCOPE`, and `CANNOT_PROCEED`
- the free-form CLI clarification loop
- `MarketDataAgent` plus a mock-backed first slice
- the initial orchestration service wiring

### Facilitator Provides

- a Spring Boot skeleton
- OpenAI starter wiring
- a simple request/response shape
- a test profile that can run without model credentials

### Validation

Automated:

- `./gradlew test`

Manual:

- run `./gradlew bootRun`
- enter `What's the current price?`
- confirm the coordinator asks for the ticker
- answer `AAPL`
- confirm only `MARKET_DATA` is selected

## Checkpoint 2: Real Market Data

### Learners Implement

- Twelve Data configuration
- the real market-data provider
- normalization from raw Twelve Data responses into `MarketSnapshot`

### Facilitator Provides

- the existing `MarketDataAgent`
- the mock-provider version from Checkpoint 1
- local config loading through `application-local.properties`

### Validation

Automated:

- `./gradlew test`

Manual:

- run `./gradlew bootRun`
- enter `What's the current price?`
- answer `AAPL`
- confirm `Source: twelve-data`

## Checkpoint 3: Fundamentals

### Learners Implement

- SEC configuration including `User-Agent`
- ticker-to-CIK resolution
- normalized fundamentals snapshot
- `FundamentalsAgent`
- orchestration wiring for `FUNDAMENTALS`

### Facilitator Provides

- the existing coordinator flow
- the current response/CLI structure
- the market-data slice from Checkpoint 2

### Validation

Automated:

- `./gradlew test`

Manual:

- run `./gradlew bootRun`
- enter `How do AAPL fundamentals look?`
- confirm `Selected agents` contains `FUNDAMENTALS`
- confirm `Source: sec`

## Checkpoint 4: News

### Learners Implement

- `NewsSnapshot` and `NewsItem`
- SEC filing normalization for official company-event signals
- Tavily web-news enrichment
- `NewsAgent`
- direct-answer behavior for news-only requests

### Facilitator Provides

- the existing fundamentals and market slices
- the shared orchestration contracts
- optional Tavily configuration support

### Validation

Automated:

- `./gradlew test`

Manual:

- run `./gradlew bootRun`
- enter `What recent news should I know about Apple?`
- confirm `Selected agents` contains `NEWS`
- confirm `Source: sec` or `Source: sec+tavily`

## Checkpoint 5: Technical Analysis

### Learners Implement

- `TechnicalAnalysisSnapshot`
- Twelve Data time-series retrieval
- Java calculations for `SMA(20)`, `EMA(20)`, and `RSI(14)`
- `TechnicalAnalysisAgent`
- direct-answer behavior for technical-only requests

### Facilitator Provides

- the existing Twelve Data configuration
- the established agent package structure
- orchestration hooks for adding one more specialized agent

### Validation

Automated:

- `./gradlew test`

Manual:

- run `./gradlew bootRun`
- enter `What do the technicals look like for Apple?`
- confirm `Selected agents` contains `TECHNICAL_ANALYSIS`
- confirm `Source: twelve-data`

## Checkpoint 6: Synthesis

### Learners Implement

- the real LLM-backed synthesis prompt
- the structured synthesis input
- the structured synthesis response type
- deterministic fallback for tests and no-model runs

### Facilitator Provides

- structured outputs from market, fundamentals, news, and technical analysis
- a broad analysis prompt for manual validation

### Validation

Automated:

- `./gradlew test`

Manual:

- run `./gradlew bootRun`
- enter `Give me a full view on Apple with fundamentals, news, and technical analysis`
- confirm the final answer is synthesized from the structured agent outputs

## Checkpoint 7: Dynamic Orchestration

### Learners Implement

- dispatch from the coordinator plan instead of a fixed chain
- shared execution state
- per-agent degraded execution
- synthesis from partial success when necessary

### Facilitator Provides

- all specialized agents from earlier checkpoints
- one forced-failure test scenario

### Validation

Automated:

- `./gradlew test`

Manual:

- run `./gradlew bootRun`
- enter `Give me a full view on Apple with fundamentals, news, and technical analysis`
- confirm multiple selected agents execute from the plan
- confirm failures, if they occur, are shown as agent-level outcomes instead of crashing the whole run

## Checkpoint 8: Parallel Fan-Out

### Learners Implement

- a Spring-managed executor for agent work
- `CompletableFuture` fan-out for independent specialized agents
- stable result merging
- dependency handling so fundamentals can still use market-price context when available

### Facilitator Provides

- the existing dynamic orchestration slice
- a concurrency-focused regression test shape

### Validation

Automated:

- `./gradlew test`
- confirm there is a dedicated orchestration test proving independent agents can start together

Manual:

- run `./gradlew bootRun`
- enter `Give me a full view on Apple with fundamentals, news, and technical analysis`
- confirm the CLI output still looks stable even though the selected agents now execute concurrently under the hood

## Checkpoint 9: Redis Caching

### Learners Implement

- Spring cache configuration backed by Redis
- a small cache helper that deduplicates repeated loads
- provider-level caching for market data, technical analysis, SEC company data, SEC submissions, and Tavily results
- a shared SEC ticker lookup service
- a local `compose.yaml` for Redis

### Facilitator Provides

- the current provider seams
- the dynamic orchestration flow that already makes those providers valuable
- the local config pattern through `application-local.properties`

### Validation

Automated:

- `./gradlew test`
- confirm there are cache-focused regression tests for repeated lookups

Manual:

- run `docker compose up -d redis`
- run `./gradlew bootRun`
- enter `Give me a full view on Apple with fundamentals, news, and technical analysis`
- confirm the full analysis still works with Redis running as the cache backend

## Checkpoint 10: Tool-Backed Market Data Agent

### Learners Implement

- a `MarketDataTools` component with a coarse `getMarketSnapshot` Spring AI tool
- a tool-aware `ChatClient` inside `MarketDataAgent`
- a market-data prompt that requires tool usage before returning a completed result
- a deterministic fallback path for test and no-model runs
- direct-answer wiring so market-only requests can reuse the market agent's own message

### Facilitator Provides

- the cached Twelve Data provider seam from earlier checkpoints
- the Redis cache layer that prevents duplicate upstream calls
- the current coordinator and orchestration flow

### Validation

Automated:

- `./gradlew test`
- confirm there is a dedicated market-data agent test covering the no-model fallback path

Manual:

- run `docker compose up -d redis`
- run `./gradlew bootRun`
- enter `What's the current price?`
- answer `AAPL`
- confirm `Selected agents` contains only `MARKET_DATA`
- confirm the request still succeeds and returns a direct market answer

### Teaching Point

This is the first specialist-agent conversion pattern. The LLM now decides how to use a bounded tool inside `MarketDataAgent`, but the actual provider call still goes through the cache-aware service layer so repeated upstream requests stay controlled.

## Checkpoint 11: Tool-Backed Fundamentals Agent

### Learners Implement

- a `FundamentalsTools` wrapper with a coarse `getFundamentalsSnapshot` Spring AI tool
- a tool-aware `ChatClient` inside `FundamentalsAgent`
- a fundamentals prompt that requires tool usage before returning a completed result
- a deterministic fallback path for test and no-model runs
- direct-answer wiring so fundamentals-only requests can reuse the fundamentals agent's own message

### Facilitator Provides

- the cached SEC provider seam from earlier checkpoints
- the existing market-context handoff from orchestration into fundamentals
- the Redis cache layer that already protects upstream provider calls

### Validation

Automated:

- `./gradlew test`
- confirm there is a dedicated fundamentals agent test covering the no-model fallback path

Manual:

- run `docker compose up -d redis`
- run `./gradlew bootRun`
- enter `How strong are Apple's fundamentals right now?`
- answer `AAPL` if the coordinator asks for a ticker
- confirm `Selected agents` contains `FUNDAMENTALS`
- confirm the request returns a direct fundamentals answer

### Teaching Point

This is the second specialist-agent conversion pattern. The LLM now controls a bounded SEC-backed tool inside `FundamentalsAgent`, but the actual SEC and optional market-context retrieval still stay behind the cache-aware provider layer so we do not lose control over duplication or cost.

## Checkpoint 12: Tool-Backed Technical Analysis Agent

### Learners Implement

- a `TechnicalAnalysisTools` wrapper with a coarse `getTechnicalAnalysisSnapshot` Spring AI tool
- a tool-aware `ChatClient` inside `TechnicalAnalysisAgent`
- a technical-analysis prompt that requires tool usage before returning a completed result
- a deterministic fallback path for test and no-model runs
- direct-answer wiring so technical-only requests can reuse the technical-analysis agent's own message

### Facilitator Provides

- the cached Twelve Data technical-analysis provider seam from earlier checkpoints
- the existing indicator calculations in Java
- the Redis cache layer that already protects upstream provider calls

### Validation

Automated:

- `./gradlew test`
- confirm there is a dedicated technical-analysis agent test covering the no-model fallback path

Manual:

- run `docker compose up -d redis`
- run `./gradlew bootRun`
- enter `What do the technicals look like for Apple?`
- answer `AAPL` if the coordinator asks for a ticker
- confirm `Selected agents` contains `TECHNICAL_ANALYSIS`
- confirm the request returns a direct technical answer

### Teaching Point

This is the third specialist-agent conversion pattern. The LLM now controls a bounded technical-analysis tool inside `TechnicalAnalysisAgent`, but the actual indicator calculations still stay deterministic in Java and the external call still goes through the cache-aware provider layer.

## Checkpoint 13: Tool-Backed News Agent

### Learners Implement

- a `NewsTools` wrapper with a coarse `getNewsSnapshot` Spring AI tool
- a tool-aware `ChatClient` inside `NewsAgent`
- a news prompt that requires tool usage before returning a completed result
- a deterministic fallback path for test and no-model runs
- direct-answer wiring so news-only requests can reuse the news agent's own message

### Facilitator Provides

- the cached hybrid news provider seam from earlier checkpoints
- the existing SEC-plus-Tavily merge behavior
- the Redis cache layer that already protects upstream provider calls

### Validation

Automated:

- `./gradlew test`
- confirm there is a dedicated news agent test covering the no-model fallback path

Manual:

- run `docker compose up -d redis`
- run `./gradlew bootRun`
- enter `What recent news should I know about Apple?`
- answer `AAPL` if the coordinator asks for a ticker
- confirm `Selected agents` contains `NEWS`
- confirm the request returns a direct news answer

### Teaching Point

This is the fourth specialist-agent conversion pattern. The LLM now controls a bounded hybrid-news tool inside `NewsAgent`, but the actual SEC and Tavily retrieval still stays deterministic behind the cache-aware provider layer.

## Delivery Recommendation

If time is tight, treat these as the must-hit live checkpoints:

1. Checkpoint 1 for orchestration and clarification
2. Checkpoint 3 for SEC-backed fundamentals
3. Checkpoint 4 for hybrid news
4. Checkpoint 6 for real synthesis
5. Checkpoint 8 for true orchestration maturity
6. Checkpoint 9 for production-minded provider caching
7. Checkpoint 10 for the first tool-backed specialist agent
8. Checkpoint 11 for the second tool-backed specialist agent
9. Checkpoint 12 for the third tool-backed specialist agent
10. Checkpoint 13 for the fourth tool-backed specialist agent
