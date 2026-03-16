# Stock Analysis Agent Workshop

This repository is the final implementation target for a workshop about building a stock-analysis multi-agent orchestration application with Spring AI.

## Workshop Goal

Build a predictable orchestration system where:

- a client sends a stock-analysis request
- a coordinator decides which specialized agents are relevant
- agents fetch deterministic data from external providers
- a synthesis step returns a grounded final answer

## Current Direction

- orchestration over workflow
- deterministic data providers over model-generated facts
- Spring AI for interpretation and synthesis
- free data sources where possible

## Provider Strategy

- market data: Twelve Data
- fundamentals: SEC EDGAR / XBRL
- news: hybrid SEC filings plus Tavily web search
- technical analysis: Twelve Data time series with Java-calculated indicators
- current runtime default: Twelve Data
- current fundamentals default: SEC
- current news default: SEC plus optional Tavily enrichment
- current technical-analysis default: Twelve Data
- test profile default: mock market data provider

If you want to force mock market data locally, use:

```bash
STOCK_ANALYSIS_MARKET_DATA_PROVIDER=mock
```

## Planning Docs

- `docs/workshop-plan.md` is the source of truth for milestone status, next steps, and deferred scope
- `docs/workshop-instructions.md` holds the learner-facing workshop instructions that evolve with the implementation

## Local Config

For local development, you can keep secrets and per-machine settings in a git-ignored file at the repo root:

- [application-local.properties.example](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/application-local.properties.example)

Create `application-local.properties` and add values like:

```properties
spring.ai.openai.api-key=YOUR_OPENAI_KEY
stock-analysis.market-data.twelve-data.api-key=YOUR_TWELVE_DATA_API_KEY
stock-analysis.news.tavily.api-key=YOUR_TAVILY_API_KEY
stock-analysis.sec.user-agent=stock-analysis-agent your-email@example.com
```

The app will load that file automatically if it exists.

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
- a fundamentals snapshot when fundamentals are selected
- a news snapshot when news is selected
- a technical-analysis snapshot when technical analysis is selected
- a grounded response based on the currently implemented agents

When multiple specialized agents are selected, `SynthesisAgent` now uses Spring AI to produce the final grounded response from the structured agent outputs. In test and no-model setups, it falls back to a deterministic synthesis so the workshop remains runnable.

## CLI Mode

You can also test the current slice through the CLI:

```bash
./gradlew bootRun
```

The CLI will prompt for one free-form request in the terminal.
The CLI accepts one free-form request and the coordinator can ask follow-up questions if information is missing.

Example:

- `What's the current price?` -> coordinator asks which ticker
- `AAPL` -> coordinator completes the request and routes the agents

If you want to run the original workshop slice with mock market data instead:

```bash
STOCK_ANALYSIS_MARKET_DATA_PROVIDER=mock \
./gradlew bootRun
```

When Twelve Data mode is active, the CLI output should show `Source: twelve-data`.
When mock mode is active, it should show `Source: mock`.

To run a fundamentals-only question backed by the SEC APIs:

```bash
./gradlew bootRun
```

Then ask:

- `How do AAPL fundamentals look?`

The CLI should execute `FUNDAMENTALS` and print a fundamentals snapshot with `Source: sec`.

To run a news-focused question with official SEC signals and optional Tavily web search:

```bash
./gradlew bootRun
```

Then ask:

- `What recent news should I know about Apple?`

The CLI should execute `NEWS` and print a recent filing snapshot with `Source: sec`.
If a Tavily key is configured, the CLI should also print a `Web news` section and the source should become `sec+tavily`.
This hybrid setup keeps official SEC disclosures while adding broader investor-relevant web coverage.

To run a technical-analysis question backed by Twelve Data time series:

```bash
./gradlew bootRun
```

Then ask:

- `What do the technicals look like for Apple?`

The CLI should execute `TECHNICAL_ANALYSIS` and print indicators such as `SMA(20)`, `EMA(20)`, and `RSI(14)` with `Source: twelve-data`.

To run a broad multi-agent question that exercises the LLM-backed synthesis step:

```bash
./gradlew bootRun
```

Then ask:

- `Give me a full view on Apple with fundamentals, news, and technical analysis`

With a configured chat model, the final answer should be synthesized by the model from the structured outputs of the specialized agents.

`OPENAI_API_KEY` is the preferred env var for this repo. `SPRING_AI_OPENAI_API_KEY` also works.
Environment variables still work, but `application-local.properties` is the simplest local setup.

If you want to validate the workshop slice without model credentials, use:

```bash
./gradlew test
```

If you want to run the HTTP API instead of the CLI, disable CLI mode explicitly:

```bash
./gradlew bootRun --args='--app.cli.enabled=false'
```
