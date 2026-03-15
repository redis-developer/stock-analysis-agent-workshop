# Workshop Plan

Last updated: 2026-03-16

## Goal

Build a stock-analysis multi-agent orchestration application with Spring AI where:

- a client sends a stock-analysis request
- a coordinator builds an `ExecutionPlan`
- only the relevant agents run
- agents fetch deterministic data from external providers
- a synthesis step returns a grounded answer
- the application handles partial progress cleanly

## Guiding Decisions

- Orchestration over fixed workflow.
- Deterministic provider calls over model-generated facts.
- REST-first entrypoint for the workshop.
- Keep integrations swappable, but avoid abstractions before the workshop needs them.
- Keep the first implementation simple before adding parallelism or advanced runtime behavior.
- Every milestone must end in a testable workshop slice.

## Status Snapshot

- Completed
  - Foundation milestone
  - Thin vertical slice with mock market data
  - LLM-based coordinator routing refactor
  - CLI testing mode for the current slice
  - Coordinator clarification loop for incomplete CLI requests
  - Spring AI starter switched to direct OpenAI
- In progress
  - Real data integration milestone
- Next up
  - Alpha Vantage market data adapter
  - SEC fundamentals data objects
  - fundamentals agent

## Milestones

| Milestone | Status | Definition of done |
| --- | --- | --- |
| Foundation | Complete | The analysis API exists, core orchestration types are defined, the test profile runs without model credentials, and the baseline test suite passes. |
| Thin Vertical Slice | Complete | `Coordinator -> MarketDataAgent -> SynthesisAgent` works end to end with mock data, the coordinator routes through a single LLM-backed concrete class, and tests pass with a simple routing override for local verification. |
| Real Data Integration | In progress | Alpha Vantage replaces the mock market provider, SEC-backed fundamentals data objects are introduced, and the real-data market slice has both automated and manual verification steps. |
| True Orchestration | Planned | The coordinator selects agents dynamically, execution no longer looks like a fixed pipeline, partial failure handling is supported, safe parallel fan-out is introduced, and routing plus degraded execution paths are covered by tests. |
| Additional Agents | Planned | Fundamentals, news, and technical analysis agents are implemented against stable data shapes and each new agent adds its own smoke-test scenario. |
| Hardening | Planned | Timeouts, retries, caching, stronger tests, and workshop checkpoints are in place, with a regression suite that validates the main workshop flows. |
| Workshop Polish | Planned | Learner instructions, checkpoints, and facilitator notes are aligned with the final implementation, and each milestone has a clear validation recipe. |

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
- `CommandLineRunner` enables CLI mode when `app.cli.enabled=true`.
- `agent/CliOrchestrationService` starts from a free-form request, loops on coordinator follow-up prompts, and prints the current slice in a console-friendly format.
- the staged CLI output is only a teaching view for the current slice and should not be mistaken for the final architecture.
- `agent/AgentOrchestrationService` coordinates the current slice.
- `agent/coordinatoragent/CoordinatorAgent` owns coordinator execution, plan normalization, and request normalization.
- `agent/coordinatoragent/CoordinatorRoutingAgent` is a concrete class that uses Spring AI structured output plus lightweight chat memory for runtime routing and clarification.
- the project now uses the Spring AI OpenAI starter for local model access.
- `agent/marketdataagent/MarketDataAgent` executes the deterministic market-data step.
- `agent/marketdataagent/MarketDataResult` holds the market-agent result.
- `marketdata/MockMarketDataProvider` is the current development provider.
- `agent/synthesisagent/SynthesisAgent` currently acts as a lightweight placeholder so the first slice can finish end to end.
- once fundamentals, news, and technical analysis are real, `agent/synthesisagent/SynthesisAgent` should be promoted into a true LLM-backed synthesis agent that consumes structured outputs from the specialized agents.
- Integration and orchestration tests are green and provide a simple routing override for repeatable verification.

## Package Shape

The repository now follows the same broad structure as the `socialmediatracker` reference project:

- `agent`
  - shared orchestration classes
  - one subpackage per agent
- `marketdata`
  - provider implementations and external data integration code
- `api`
  - REST adapters and HTTP request/response models

This keeps the workshop centered on agents rather than on a generic `analysis` package.

## Immediate Backlog

1. Add Alpha Vantage configuration properties and a client wrapper.
2. Implement `AlphaVantageMarketDataProvider` behind the existing market-data seam.
3. Add normalization tests for market snapshots so provider changes remain safe.
4. Add a CLI smoke-test recipe for coordinator clarification with configured model credentials.
5. Define SEC fundamentals data objects and a client wrapper.
6. Implement the fundamentals agent using SEC data plus market price when needed.
7. Promote `SynthesisAgent` from a placeholder formatter into a true LLM-backed agent once multiple analysis agents are available.
8. Refine the coordinator prompt and routing decision shape once the fundamentals path is real.

## Deferred Scope

- UI
- chat memory
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
