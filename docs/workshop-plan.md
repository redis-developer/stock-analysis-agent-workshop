# Workshop Plan

Last updated: 2026-03-16

## Goal

Build a stock-analysis multi-agent orchestration application with Spring AI where:

- a client can hold a stock-analysis conversation
- a coordinator builds an `ExecutionPlan`
- only the relevant agents run
- agents fetch deterministic data from external providers
- a synthesis step returns a grounded answer
- the application handles partial progress cleanly
- memory can carry follow-up context across turns

## Guiding Decisions

- Orchestration over fixed workflow.
- Deterministic provider calls over model-generated facts.
- keep the REST endpoint, but treat the memory-backed chat frontend as the primary workshop path now.
- Keep integrations swappable, but avoid abstractions before the workshop needs them.
- Keep the first implementation simple before adding parallelism or advanced runtime behavior.
- Every milestone must end in a testable workshop slice.
- put memory at the chat boundary, not inside agent-to-agent handoffs.

## Status Snapshot

- Completed
  - Foundation milestone
  - Thin vertical slice with mock market data
  - LLM-based coordinator routing refactor
  - CLI testing mode for the current slice
  - Coordinator clarification loop for incomplete CLI requests
  - Spring AI starter switched to direct OpenAI
  - Twelve Data market-data provider and normalization tests
  - SEC fundamentals provider, fundamentals agent, and normalization tests
  - Hybrid news agent with SEC signals, Tavily web search, and normalization tests
  - Technical analysis agent with Twelve Data time series and indicator tests
  - LLM-backed synthesis agent with deterministic fallback for test/no-model runs
  - Dynamic orchestration dispatch with per-agent failure handling
  - Safe parallel fan-out for independent agents via `CompletableFuture`
  - Redis-backed provider caching with duplicate-call protection and local Docker Compose support
  - Market data converted into the first tool-backed LLM specialist agent
  - Fundamentals converted into the second tool-backed LLM specialist agent
  - Technical analysis converted into the third tool-backed LLM specialist agent
  - News converted into the fourth tool-backed LLM specialist agent
  - Redis Agent Memory-backed chat mode with working-memory and long-term-memory retrieval before coordinator routing
- In progress
  - Hardening milestone
  - Workshop polish milestone
- Next up
  - add timeouts and retry boundaries around external providers
  - add workshop checkpoint tags or branches when the teaching path is stable
  - decide whether the next delivery slice should be observability, an HTTP chat endpoint, or checkpoint packaging

## Milestones

| Milestone | Status | Definition of done |
| --- | --- | --- |
| Foundation | Complete | The analysis API exists, core orchestration types are defined, the test profile runs without model credentials, and the baseline test suite passes. |
| Thin Vertical Slice | Complete | `Coordinator -> MarketDataAgent -> SynthesisAgent` works end to end with mock data, the coordinator routes through a single LLM-backed concrete class, and tests pass with a simple routing override for local verification. |
| Real Data Integration | Complete | Twelve Data replaces the mock market provider, SEC-backed fundamentals data objects are introduced, and the real-data slices have both automated and manual verification steps. |
| True Orchestration | Complete | The coordinator selects agents dynamically, execution no longer looks like a fixed pipeline, partial failure handling is supported, safe parallel fan-out is introduced, and routing plus degraded execution paths are covered by tests. |
| Additional Agents | Complete | Fundamentals, news, and technical analysis agents are implemented against stable data shapes and each new agent adds its own smoke-test scenario. |
| Conversational Memory | Complete | The frontend runs as a memory-backed chat, Redis Agent Memory provides working and long-term memory context through advisors on the coordinator path, and follow-up questions can reuse context from prior turns. |
| Hardening | In Progress | Timeouts, retries, caching, stronger tests, and workshop checkpoints are in place, with a regression suite that validates the main workshop flows. |
| Workshop Polish | In Progress | Learner instructions, checkpoints, and facilitator notes are aligned with the final implementation, and each milestone has a clear validation recipe. |

## Milestone Testability Rule

Every milestone must end with three things:

1. Automated verification
   - unit and integration tests for the slice added in that milestone
2. Manual smoke test
   - one short command or request that proves the milestone works locally
3. Workshop validation scenario
   - one learner-facing scenario that demonstrates the concept taught in that milestone

If a milestone cannot be verified through all three, it is not done.

## Current Architecture

- `POST /analysis` is the current HTTP entrypoint.
- `chat/StockAnalysisChatService` is the user-facing chat layer and remains a deterministic shell around the coordinator path.
- `chat/StockAnalysisChatTools` exposes the bounded stock-analysis entrypoint that delegates into the existing coordinator and orchestration stack.
- `memory/AmsChatMemoryRepository` adapts Redis Agent Memory Server into Spring AI `ChatMemoryRepository`.
- `memory/LongTermMemoryAdvisor` injects relevant long-term memories into the coordinator request before each model call.
- the frontend chat is conversational now, but the orchestration underneath is still explicit application code rather than a hidden workflow.
- `agent/AgentOrchestrationService` coordinates the current slice.
- `agent/AgentOrchestrationService` now dispatches selected agents dynamically instead of hardcoding one growing execution chain.
- `agent/AgentOrchestrationService` now fans out independent selected agents with `CompletableFuture` on a Spring-managed executor and merges results back in plan order for stable frontend/API output.
- `agent/coordinatoragent/CoordinatorAgent` owns coordinator execution, plan normalization, and request normalization.
- `agent/coordinatoragent/CoordinatorRoutingAgent` is a concrete class that uses Spring AI structured output plus memory advisors for runtime routing and clarification.
- the project now uses the Spring AI OpenAI starter for local model access.
- `agent/marketdataagent/MarketDataAgent` is now the first tool-backed specialist agent and uses Spring AI tool-calling against the cached market-data provider.
- `agent/marketdataagent/MarketDataResult` holds the market-agent result.
- `agent/fundamentalsagent/FundamentalsAgent` is now the second tool-backed specialist agent and uses Spring AI tool-calling against the cached fundamentals provider.
- `agent/fundamentalsagent/FundamentalsResult` holds the fundamentals-agent result.
- `agent/newsagent/NewsAgent` is now the fourth tool-backed specialist agent and uses Spring AI tool-calling against the cached hybrid news provider.
- `agent/newsagent/NewsResult` holds the news-agent result.
- `agent/technicalanalysisagent/TechnicalAnalysisAgent` is now the third tool-backed specialist agent and uses Spring AI tool-calling against the cached technical-analysis provider.
- `agent/technicalanalysisagent/TechnicalAnalysisResult` holds the technical-analysis result.
- `marketdata/MockMarketDataProvider` is the current development provider.
- `marketdata/twelvedata/TwelveDataMarketDataProvider` replaces the mock provider without changing the orchestration layer.
- `fundamentals/sec/SecFundamentalsProvider` resolves ticker-to-CIK and normalizes SEC company facts into the fundamentals contract.
- `news/sec/SecNewsProvider` resolves ticker-to-CIK and normalizes recent SEC filings into official company-event signals.
- `news/tavily/TavilyNewsProvider` enriches the news snapshot with investor-relevant web coverage when a Tavily key is configured.
- `technicalanalysis/twelvedata/TwelveDataTechnicalAnalysisProvider` retrieves time-series data and computes SMA, EMA, and RSI in Java.
- `cache/ExternalDataCache` protects provider calls from duplicate execution and currently uses Redis as the main cache backend.
- `sec/SecTickerLookupService` centralizes SEC ticker-to-CIK resolution so fundamentals and news do not fetch the ticker file independently.
- `compose.yaml` now provisions Redis, Agent Memory Server, and Redis Insight for the local workshop stack.
- local secrets and per-machine settings can live in an optional git-ignored `application-local.properties` file at the repository root.
- a git-ignored `.env` file can provide `OPENAI_API_KEY` to Docker Compose for the local Agent Memory Server.
- `agent/synthesisagent/SynthesisAgent` is now a true LLM-backed agent at runtime and consumes only structured outputs from the specialized agents.
- in test and no-model runs, `agent/synthesisagent/SynthesisAgent` falls back to a deterministic synthesis path so the workshop remains runnable without credentials.
- selected agents can now fail independently without crashing the entire request; failures are surfaced in agent execution status and limitations.
- the current runtime parallelizes independent specialized agents but still lets dependent work, such as fundamentals with market-price context, wait for the data it needs.
- repeated external provider calls are now deduplicated through the cache layer, which is important both for the current orchestration flow and for future tool-backed specialist agents.
- market data is the first specialist agent using Spring AI tools, which gives the workshop a concrete pattern for converting more specialized agents later.
- fundamentals now follows the same tool-backed specialist pattern and can reuse market context from orchestration without re-triggering market APIs.
- technical analysis now follows the same tool-backed specialist pattern while still keeping the indicator calculations deterministic in Java.
- news now follows the same tool-backed specialist pattern while still keeping the SEC-plus-Tavily retrieval deterministic behind the cached provider layer.
- the default chat path now uses both short-term and long-term memory before deciding whether to call the bounded stock-analysis tool.
- Integration and orchestration tests are green and provide a simple routing override for repeatable verification.
- the repository now includes separate learner instructions, checkpoint mapping, and facilitator notes so the teaching path is no longer implicit.

## Package Shape

The repository now follows the same broad structure as the `socialmediatracker` reference project:

- `agent`
  - shared orchestration classes
  - one subpackage per agent
- `chat`
  - the user-facing chat layer and bounded tool surface
- `memory`
  - Spring AI chat-memory repository glue for Redis Agent Memory
- `marketdata`
  - provider implementations and external data integration code
- `api`
  - REST adapters and HTTP request/response models

This keeps the workshop centered on agents rather than on a generic `analysis` package.

## Immediate Backlog

1. Add timeouts and retry boundaries around Twelve Data, SEC, and Tavily calls.
2. Add checkpoint tags or branches and facilitator smoke-test scripts.
3. Continue improving the browser chat experience as the main workshop interface.

## Deferred Scope

- UI
- autonomous planning
- generic agent framework abstractions
- multiple model providers
- Slack interfaces
- vector retrieval unless a later workshop part truly needs it

## Maintenance Rule

Update this file after each meaningful slice by changing:

- the status snapshot
- the active milestone
- the immediate backlog
- any architectural decision that affects the workshop path
- the automated and manual validation notes for the milestone that moved
