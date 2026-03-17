# Stock Analysis Agent Workshop

This repository is the final implementation target for a workshop about building a stock-analysis multi-agent orchestration application with Spring AI.
The current default experience is a memory-backed chat CLI layered on top of the orchestration flow.

## Workshop Goal

Build a predictable orchestration system where:

- a client can hold a stock-analysis conversation
- a coordinator decides which specialized agents are relevant
- agents fetch deterministic data from external providers
- a synthesis step returns a grounded final answer
- one agent failing does not crash the whole analysis
- independent agents can fan out safely in parallel
- chat memory supports follow-up questions across turns

## Current Direction

- orchestration over workflow
- deterministic data providers over model-generated facts
- Spring AI for interpretation and synthesis
- free data sources where possible
- memory-backed chat over one-shot prompts for the main workshop path

## Provider Strategy

- market data: Twelve Data
- fundamentals: SEC EDGAR / XBRL
- news: hybrid SEC filings plus Tavily web search
- technical analysis: Twelve Data time series with Java-calculated indicators
- market data agent: now LLM-backed with Spring AI tools over the cached provider layer
- fundamentals agent: now LLM-backed with Spring AI tools over the cached SEC provider layer
- technical-analysis agent: now LLM-backed with Spring AI tools over the cached Twelve Data provider layer
- news agent: now LLM-backed with Spring AI tools over the cached SEC-plus-Tavily provider layer
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
- `docs/workshop-checkpoints.md` maps the workshop into explicit checkpoints with learner scope and validation steps
- `docs/facilitator-notes.md` captures local setup, live-demo guidance, and common failure modes

## Local Infrastructure

Redis is the current cache backend for external provider calls, and the same local stack now also runs Redis Agent Memory for the chat session.

Create a local `.env` file from [/.env.example](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/.env.example) or export `OPENAI_API_KEY` in your shell, then start the local stack with:

```bash
docker compose up -d redis agent-memory-server redis-insight
```

The repository includes [compose.yaml](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/compose.yaml) for that setup.
On first boot, `agent-memory-server` may need outbound network access to download tokenizer assets used for working-memory token calculations.

## Local Config

For local development, you can keep secrets and per-machine settings in a git-ignored file at the repo root:

- [application-local.properties.example](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/application-local.properties.example)
- [/.env.example](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/.env.example)

Create `application-local.properties` and add values like:

```properties
spring.ai.openai.api-key=YOUR_OPENAI_KEY
stock-analysis.market-data.twelve-data.api-key=YOUR_TWELVE_DATA_API_KEY
stock-analysis.news.tavily.api-key=YOUR_TAVILY_API_KEY
stock-analysis.sec.user-agent=stock-analysis-agent your-email@example.com
agent-memory.server.url=http://localhost:8000
```

The app will load that file automatically if it exists.
Docker Compose reads `.env`, so that is the right place for `OPENAI_API_KEY` when starting `agent-memory-server`.
If you ever want to bypass Redis locally, you can set `spring.cache.type=simple`.

## Non-Goals For The First Slice

- UI
- autonomous planning
- generic agent framework abstractions
- multiple model providers
- Slack interfaces

## Current Interfaces

`POST /analysis`

```json
{
  "ticker": "AAPL",
  "question": "What is the stock price right now?"
}
```

The REST API remains the stateless, one-shot integration surface.

It returns:

- the execution plan
- current agent execution status
- a market snapshot from the configured provider
- a fundamentals snapshot when fundamentals are selected
- a news snapshot when news is selected
- a technical-analysis snapshot when technical analysis is selected
- a grounded response based on the currently implemented agents

The primary workshop path is now the CLI chat:

- user messages go through a memory-backed `ChatClient`
- Spring AI advisors inject short-term and long-term memory
- the chat layer calls the orchestration stack through a bounded stock-analysis tool
- the underlying coordinator, specialized agents, and synthesis flow remain explicit application code
- if Agent Memory has a transient write problem, the chat now keeps answering and degrades memory behavior instead of failing the whole turn

When multiple specialized agents are selected, `SynthesisAgent` uses Spring AI to produce the final grounded response from the structured agent outputs. In test and no-model setups, it falls back to a deterministic synthesis so the workshop remains runnable.
The orchestration layer dispatches agents dynamically from the execution plan and degrades cleanly when one selected agent fails.
Independent agents fan out through `CompletableFuture` on a Spring-managed executor, while synthesis still waits for the selected analysis tasks to finish.
External provider calls go through a Redis-backed cache layer so repeated market, SEC, and Tavily lookups are not triggered unnecessarily.
`MarketDataAgent`, `FundamentalsAgent`, `TechnicalAnalysisAgent`, and `NewsAgent` are now tool-backed specialist agents that use Spring AI over the cached provider layer.

## CLI Mode

You can test the current system through the memory-backed chat CLI:

```bash
./gradlew bootRun
```

The CLI starts a chat session and stores memory under a conversation id shaped like `userId:sessionId`.
The chat layer uses:

- Spring AI `MessageChatMemoryAdvisor` for working memory
- a custom `LongTermMemoryAdvisor` backed by Redis Agent Memory
- a single stock-analysis tool that delegates to the existing orchestration stack

Useful commands:

- `/exit` ends the session
- `/clear` clears the current chat memory

To run the full local chat stack:

```bash
docker compose up -d redis agent-memory-server redis-insight
./gradlew bootRun
```

Recommended chat prompts:

- `What's the current price of Apple?`
- `What about its fundamentals?`
- `And any recent news?`
- `What do the technicals look like?`
- `Give me a full view with price, fundamentals, news, and technical analysis`

You should not need to repeat `AAPL` once the conversation already established the company.
When memory is active, the CLI also prints working-memory usage and any retrieved long-term memories returned by the advisor layer.
If Agent Memory is temporarily unavailable, the chat should still answer, but follow-up context may be weaker until the memory service recovers.

If you want to run the chat layer without the live Redis-backed provider cache, you can fall back to simple local caching:

```bash
SPRING_CACHE_TYPE=simple \
./gradlew bootRun
```

If you want to run the HTTP API instead of the chat CLI, disable CLI mode explicitly:

```bash
./gradlew bootRun --args='--app.cli.enabled=false'
```

`OPENAI_API_KEY` is the preferred env var for this repo. `SPRING_AI_OPENAI_API_KEY` also works.
Environment variables still work, but `application-local.properties` plus `.env` is the simplest local setup.

If you want to validate the workshop slice without model credentials, use:

```bash
./gradlew test
```

## Java 25 And Netty On macOS

This project uses Reactor Netty transitively through Spring AI's WebClient support. On macOS with Java 25, you may otherwise see:

- a restricted native-access warning from Netty
- a fallback warning about `MacOSDnsServerAddressStreamProvider`

The Gradle build already configures the required JVM flag for `bootRun` and `test`:

```bash
--enable-native-access=ALL-UNNAMED
```

If you run the app outside Gradle, add the same JVM argument yourself.

For IntelliJ IDEA:

- open the Run/Debug configuration for the app
- add `--enable-native-access=ALL-UNNAMED` in `VM options`

For a packaged jar:

```bash
java --enable-native-access=ALL-UNNAMED -jar build/libs/stock-analysis-agent-0.0.1-SNAPSHOT.jar
```
