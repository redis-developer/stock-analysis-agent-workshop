# Workshop Instructions

Last updated: 2026-03-16

This file is the learner-facing companion to the implementation. As the project evolves, add new workshop parts here so the exercises stay aligned with the actual repository state.

## Workshop Framing

This workshop teaches orchestration, not autonomous planning.

Learners should leave understanding how to:

- model a stock-analysis problem as specialized agents
- keep orchestration in application code
- fetch deterministic data from external providers
- use Spring AI for interpretation and synthesis instead of factual retrieval

## Part 1: Orchestration Foundation

### Objective

Build the first end-to-end slice of the application with a REST endpoint, a coordinator that can clarify missing information, one implemented agent, and a synthesis step.

### What Learners Build

1. A `POST /analysis` endpoint that accepts a ticker and a question.
2. Shared orchestration types such as `AgentType`, `ExecutionPlan`, and `AgentExecution`.
3. A `CoordinatorAgent` that delegates routing to a concrete LLM-backed `CoordinatorRoutingAgent`.
4. A structured coordinator result that can return `COMPLETED`, `NEEDS_MORE_INPUT`, `OUT_OF_SCOPE`, or `CANNOT_PROCEED`.
5. A CLI flow that starts from one free-form request and loops when the coordinator asks for clarification.
6. A `MarketDataAgent` backed by a market-data seam and a `MarketDataResult`.
7. A `MockMarketDataProvider` so the flow works before any external API integration.
8. A lightweight `SynthesisAgent` that returns a grounded answer for the implemented slice.
   In this part it behaves more like a placeholder formatter than a full synthesis agent.
9. An `AgentOrchestrationService` that wires the first flow together.
10. An LLM-based routing implementation that returns a structured `RoutingDecision`.

### Acceptance Criteria

- The test profile runs without model credentials.
- The coordinator no longer contains hard-coded keyword routing.
- The coordinator can ask for missing information before routing.
- The coordinator can reject unsupported requests gracefully.
- A simple price question can route to only `MARKET_DATA`.
- A broader question can plan additional agents plus synthesis.
- The response returns the execution plan and current agent status.
- Tests cover coordinator routing and the API happy path.

### How Learners Validate Part 1

Automated:

- run `./gradlew test`
- verify that coordinator tests use a simple routing override rather than hard-coded application routing

Manual smoke test with a configured chat model:

- run the app in CLI mode
- provide one free-form request in the terminal
- verify that `What's the current price?` triggers a follow-up question for the ticker
- answer with `AAPL`
- verify that the coordinator resolves the request and routes to only `MARKET_DATA`

CLI example:

```bash
STOCK_ANALYSIS_MARKET_DATA_PROVIDER=mock \
./gradlew bootRun
```

Recommended local setup:

- create `application-local.properties` from `application-local.properties.example`
- put your OpenAI key, Twelve Data key, and SEC user-agent there
- run `./gradlew bootRun`

Example request:

```bash
curl -X POST http://localhost:8080/analysis \
  -H "Content-Type: application/json" \
  -d '{
    "ticker": "AAPL",
    "question": "What is the stock price right now?"
  }'
```

Expected shape:

- `executionPlan.selectedAgents` contains only `MARKET_DATA`
- `executionPlan.routingReasoning` is populated
- `marketSnapshot.source` is `mock`
- `answer` is populated
- the CLI can ask a follow-up question before running agents
- the CLI exits after printing the result

If a chat model is not configured yet, use the automated test suite as the validation path for this milestone. The runtime coordinator is intentionally LLM-driven, while tests override the routing class to keep the workshop slice reproducible.

### Key Teaching Points

- Keep orchestration deterministic even when routing is model-assisted.
- Let the coordinator decide whether it can proceed, but keep the conversation loop in Java.
- The staged CLI output is just a teaching presentation for this slice; the long-term goal is dynamic orchestration, not a permanently linear workflow.
- Do not let agents call each other directly.
- Separate orchestration metadata from domain payloads.
- Use mock data first so the architecture is proven before real integrations are added.
- It is fine for the first synthesis step to be lightweight; promote it into a true agent only when there are multiple real agent outputs to combine.
- Keep the project agent-centric by organizing code under `agent/<agent-name>`.
- Use LLMs to select the route, but keep plan normalization and execution in Java.

### Suggested Exercise Flow

1. Create the request and response DTOs.
2. Add the shared orchestration classes under `agent`.
3. Implement `agent/coordinatoragent/CoordinatorAgent`.
4. Add a structured coordinator result with finish reasons and follow-up prompts.
5. Add `agent/coordinatoragent/CoordinatorRoutingAgent` as the concrete LLM-backed router.
6. Wire a CLI loop that continues while the coordinator returns `NEEDS_MORE_INPUT`.
7. Add `agent/marketdataagent/MarketDataAgent` and its result object.
8. Implement a mock provider under `marketdata`.
9. Add `agent/synthesisagent/SynthesisAgent`.
10. Wire the flow through `agent/AgentOrchestrationService`.
11. Write tests with a routing override.

## Part 2: Real Market Data

### Objective

Replace the mock market data provider with a real Twelve Data integration without changing the orchestration flow.

### Planned Scope

- add Twelve Data configuration
- implement `TwelveDataMarketDataProvider`
- keep `MarketDataAgent` unchanged at the orchestration level
- add normalization tests for provider output

### Validation Goal

- the automated suite proves provider normalization
- a manual request shows the same endpoint working with real market data
- the workshop can explain that orchestration stayed stable while the provider changed and the coordinator remained LLM-routed

### Manual Smoke Test

```bash
./gradlew bootRun
```

Then enter:

- `Request: What's the current price?`
- `Your answer: AAPL`

Expected result:

- the coordinator still resolves the request through clarification
- `Selected agents` contains only `MARKET_DATA`
- `Source` is `twelve-data`
- the final answer uses the Twelve Data quote instead of the mock snapshot

## Part 3: Fundamentals

### Objective

Introduce a fundamentals path based on SEC EDGAR / XBRL data and plug it into the coordinator.

### What Learners Build

1. SEC configuration for `data.sec.gov`, `company_tickers.json`, and a declared `User-Agent`.
2. A deterministic SEC-backed provider under `fundamentals/sec`.
3. A normalized fundamentals contract under `agent/fundamentalsagent`.
4. A `FundamentalsAgent` that can run with or without market-price context.
5. Orchestration wiring so `FUNDAMENTALS` executes when selected.
6. A response shape and CLI output that surface the fundamentals snapshot.
7. Normalization tests for SEC company facts.

### Acceptance Criteria

- SEC ticker lookup resolves a ticker into a CIK.
- SEC company facts are normalized into a stable fundamentals snapshot.
- the fundamentals path works without changing the coordinator contract
- the fundamentals agent executes when the coordinator selects `FUNDAMENTALS`
- the final response includes a fundamentals snapshot when available
- tests cover SEC normalization and the API happy path for fundamentals

### Manual Smoke Test

```bash
./gradlew bootRun
```

Then enter:

- `Request: How do AAPL fundamentals look?`

Expected result:

- `Selected agents` contains `FUNDAMENTALS`
- a fundamentals snapshot is printed in the CLI
- `Source` is `sec`
- the final answer includes the normalized fundamentals result

## Part 4: News

### Objective

Introduce a hybrid news path that keeps official company-event signals while also searching the web for investor-relevant coverage.

### What Learners Build

1. A `NewsSnapshot` and `NewsItem` contract under `agent/newsagent`.
2. A `NewsAgent` that plugs into the existing orchestration flow and merges multiple sources.
3. A deterministic SEC-backed provider under `news/sec`.
4. A Tavily-backed provider under `news/tavily`.
5. SEC submissions parsing that turns recent filings into structured official-signal items.
6. Tavily web search that turns search results into investor-relevant web-news items.
7. CLI and API output that surface both official signals and web news when `NEWS` is selected.
8. A direct-answer path for news-only questions.
9. Provider normalization tests and an integration test for the news path.

### Acceptance Criteria

- the news path resolves ticker-to-CIK through SEC ticker data
- recent SEC filings are normalized into a stable news snapshot
- Tavily results can enrich the news snapshot when a key is configured
- the coordinator can execute `NEWS` without changing its contract
- the response includes a news snapshot when available
- a news-only question can return a direct answer
- tests cover SEC filing normalization, Tavily result normalization, and the API happy path for news

### Manual Smoke Test

```bash
./gradlew bootRun
```

Then enter:

- `Request: What recent news should I know about Apple?`

Expected result:

- `Selected agents` contains `NEWS`
- a news snapshot is printed in the CLI
- `Source` is `sec` without Tavily or `sec+tavily` with Tavily configured
- the final answer references investor-relevant news and, when available, official SEC signals

### Teaching Point

For this workshop slice, the news agent is deliberately hybrid. It keeps SEC filings for official company-event signals and uses Tavily web search for broader investor-relevant coverage. That lets learners see how to mix deterministic official data with a search service without giving up orchestration control.

## Part 5: Technical Analysis

### Objective

Introduce a deterministic technical-analysis path that computes indicators in Java from Twelve Data price history.

### What Learners Build

1. A `TechnicalAnalysisSnapshot` contract under `agent/technicalanalysisagent`.
2. A `TechnicalAnalysisAgent` that plugs into the existing orchestration flow.
3. A deterministic provider under `technicalanalysis/twelvedata`.
4. Twelve Data time-series retrieval for the selected ticker.
5. Java calculations for `SMA(20)`, `EMA(20)`, and `RSI(14)`.
6. Trend and momentum labels derived from those indicators.
7. CLI and API output that surface the technical-analysis snapshot.
8. A direct-answer path for technical-only questions.
9. Provider normalization tests and an integration test for the technical path.

### Acceptance Criteria

- the technical-analysis path retrieves a stable Twelve Data time series
- indicators are calculated in Java rather than by the LLM
- the coordinator can execute `TECHNICAL_ANALYSIS` without changing its contract
- the response includes a technical-analysis snapshot when available
- a technical-only question can return a direct answer
- tests cover indicator normalization and the API happy path for technical analysis

### Manual Smoke Test

```bash
./gradlew bootRun
```

Then enter:

- `Request: What do the technicals look like for Apple?`

Expected result:

- `Selected agents` contains `TECHNICAL_ANALYSIS`
- a technical-analysis snapshot is printed in the CLI
- `Source` is `twelve-data`
- the final answer references trend, momentum, and the indicator values

### Teaching Point

This slice reinforces one of the main workshop rules: the model should not calculate market indicators. Java computes the indicators, and the orchestration layer decides when the technical-analysis agent should run.

## Part 6: Synthesis

### Objective

Replace the placeholder final-answer formatter with a true LLM-backed synthesis agent that combines the structured outputs of the specialized agents.

### What Learners Build

1. A synthesis prompt focused on combining structured market, fundamentals, news, and technical signals.
2. A runtime `ChatClient` for the synthesis agent.
3. A compact synthesis input assembled from the typed snapshot objects.
4. A structured synthesis response type.
5. A deterministic fallback path for test and no-model runs.
6. Integration and unit tests that keep the workshop reproducible without model credentials.

### Acceptance Criteria

- multi-agent requests route through a real synthesis agent at runtime
- the synthesis prompt uses only structured agent outputs
- the synthesizer does not depend on raw conversations between agents
- the test suite still passes with `spring.ai.model.chat=none`
- a broad multi-agent question returns one grounded final answer

### Manual Smoke Test

```bash
./gradlew bootRun
```

Then enter:

- `Request: Give me a full view on Apple with fundamentals, news, and technical analysis`

Expected result:

- multiple specialized agents execute
- the final answer is synthesized from their outputs
- with a configured chat model, the runtime path uses the LLM-backed synthesizer
- without a configured chat model, the deterministic fallback still produces a coherent answer

### Teaching Point

This slice is where the project fully demonstrates the workshop pattern: specialized agents gather deterministic signals, and a single synthesis agent turns those structured results into the final narrative. The model interprets; it does not retrieve or calculate the underlying facts.

## Authoring Rule

Whenever a new workshop slice lands in the codebase, update this file with:

- the new part objective
- what learners build
- acceptance criteria
- the main teaching points for that slice
- how learners validate that the slice works
