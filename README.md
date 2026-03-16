# Stock Analysis Agent Workshop

This repository is the final implementation target for a workshop about building a stock-analysis multi-agent orchestration application with Spring AI.

## Workshop Goal

Build a predictable orchestration system where:

- a client sends a stock-analysis request
- a coordinator decides which specialized agents are relevant
- agents fetch deterministic data from external providers
- a synthesis step returns a grounded answer

## Current Direction

- orchestration over workflow
- deterministic data providers over model-generated facts
- Spring AI for interpretation and synthesis
- free data sources where possible

## Provider Strategy

- market data: Alpha Vantage
- fundamentals: SEC EDGAR / XBRL
- current runtime default: Alpha Vantage
- test profile default: mock market data provider

If you want to force mock market data locally, use:

```bash
STOCK_ANALYSIS_MARKET_DATA_PROVIDER=mock
```

## Planning Docs

- `docs/workshop-plan.md` is the source of truth for milestone status, next steps, and deferred scope
- `docs/workshop-instructions.md` holds the learner-facing workshop instructions that evolve with the implementation

## Non-Goals For The First Slice

- UI
- chat memory
- autonomous planning
- generic agent framework abstractions
- multiple model providers
- Slack interfaces

## Current API

`POST /analysis`

```json
{
  "ticker": "AAPL",
  "question": "What is the stock price right now?"
}
```

The first slice returns:

- the execution plan
- current agent execution status
- a market snapshot from the configured provider
- a grounded response based on the currently implemented agents

For this first slice, `SynthesisAgent` is intentionally lightweight. It acts as a placeholder so the orchestration can finish end to end, and it will be promoted into a true LLM-backed agent once multiple analysis agents are implemented.

## CLI Mode

You can also test the current slice through the CLI:

```bash
OPENAI_API_KEY=YOUR_OPENAI_KEY \
ALPHA_VANTAGE_API_KEY=YOUR_ALPHA_VANTAGE_KEY \
./gradlew bootRun
```

The CLI will prompt for one free-form request in the terminal.
The CLI accepts one free-form request and the coordinator can ask follow-up questions if information is missing.

Example:

- `What's the current price?` -> coordinator asks which ticker
- `AAPL` -> coordinator completes the request and routes the agents

If you want to run the original workshop slice with mock market data instead:

```bash
OPENAI_API_KEY=YOUR_OPENAI_KEY \
STOCK_ANALYSIS_MARKET_DATA_PROVIDER=mock \
./gradlew bootRun
```

When Alpha Vantage mode is active, the CLI output should show `Source: alpha-vantage`.
When mock mode is active, it should show `Source: mock`.

`OPENAI_API_KEY` is the preferred env var for this repo. `SPRING_AI_OPENAI_API_KEY` also works.

If you want to validate the workshop slice without model credentials, use:

```bash
./gradlew test
```

If you want to run the HTTP API instead of the CLI, disable CLI mode explicitly:

```bash
./gradlew bootRun --args='--app.cli.enabled=false'
```
