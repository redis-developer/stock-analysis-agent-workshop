# Stock Analysis Agent Application

This module is the finished Spring Boot application.

If you worked through the workshop, this is the end state you were building toward.

If you did not work through the workshop, you can still use this module as:

- a runnable multi-agent application
- a reference implementation
- a concrete example of Spring AI plus Redis in a production-style architecture

## What This App Does

The application answers stock-analysis questions through a browser chat experience.

Under the hood, it uses:

- a coordinator to decide which specialist agents should run
- specialist agents for:
  - market data
  - fundamentals
  - news
  - technical analysis
- a synthesis agent to produce the final answer
- Redis-backed memory and caching
- observability and evaluation support

Semantic cache hits can short circuit the coordinator flow before long-term memory retrieval runs.

So when you ask a question like:

- "What's going on with Apple right now?"

the app does not rely on one giant prompt.

It routes the request, runs the relevant agents, and then synthesizes the final response.

## How to Run It

From the repository root, run:

```bash
./gradlew :stockanalysisagent:bootRun
```

Then open:

- [http://localhost:8080](http://localhost:8080)

If you are already inside this module directory, you can also run:

```bash
../gradlew bootRun
```

## Local Dependencies

For the full local experience, start the supporting services first:

```bash
docker compose up -d redis agent-memory-server redis-insight
```

If you also want tracing:

```bash
docker compose up -d zipkin
```

If you want to run the finalized app in Docker too:

```bash
docker compose up -d stock-analysis-agent
```

These services are defined in:

- [compose.yaml](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/compose.yaml)

## Local Configuration

The easiest local setup is to use:

- [/.env.example](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/.env.example)
- [application-local.properties.example](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/application-local.properties.example)

Create a repository-root `.env` or `application-local.properties` and add values like:

```dotenv
OPENAI_API_KEY=YOUR_OPENAI_KEY
TWELVE_DATA_API_KEY=YOUR_TWELVE_DATA_API_KEY
TAVILY_API_KEY=YOUR_TAVILY_API_KEY
SEC_USER_AGENT=stock-analysis-agent your-email@example.com
AGENT_MEMORY_SERVER_URL=http://localhost:8000
```

Typical things you will need:

- `OPENAI_API_KEY`
- Twelve Data API key from [Twelve Data](https://twelvedata.com/docs)
- Tavily API key from [Tavily](https://app.tavily.com/home)
- SEC user agent

The application loads `.env` and `application-local.*` automatically when those files exist in either the repository root or the module directory.

## Docker

Build and run the finalized application with Compose:

```bash
docker compose up -d stock-analysis-agent
```

Or build the image manually from the repository root:

```bash
docker build -f stockanalysisagent/Dockerfile -t stock-analysis-agent .
```

Then run it with the same environment values the app already supports:

```bash
docker run --rm -p 8080:8080 \
  -e OPENAI_API_KEY=YOUR_OPENAI_KEY \
  -e TWELVE_DATA_API_KEY=YOUR_TWELVE_DATA_API_KEY \
  -e TAVILY_API_KEY=YOUR_TAVILY_API_KEY \
  -e SEC_USER_AGENT="stock-analysis-agent your-email@example.com" \
  -e REDIS_HOST=host.docker.internal \
  -e AGENT_MEMORY_SERVER_URL=http://host.docker.internal:8000 \
  stock-analysis-agent
```

If you are already running the supporting services from [compose.yaml](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/compose.yaml), this will expose the app on:

- [http://localhost:8080](http://localhost:8080)

## Useful Commands

From the repository root:

```bash
./gradlew :stockanalysisagent:bootRun
./gradlew :stockanalysisagent:test
./gradlew :stockanalysisagent:compileJava
```

If you want to run the evaluation examples:

```bash
./gradlew :stockanalysisagent:test --tests "com.redis.stockanalysisagent.evaluation.*"
```

## What to Try in the Chat UI

Good starter prompts:

- `What's the current price of Apple?`
- `What about its fundamentals?`
- `Any recent news I should know about?`
- `What do the technicals look like?`
- `Give me a full view with price, fundamentals, news, and technical analysis`

Because the app uses memory, follow-up questions should feel more natural across turns.

## What to Look At in the Code

If you want to understand the architecture, these are good places to start:

- [CoordinatorAgent.java](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagent/src/main/java/com/redis/stockanalysisagent/agent/coordinatoragent/CoordinatorAgent.java)
- [AgentOrchestrationService.java](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagent/src/main/java/com/redis/stockanalysisagent/agent/orchestration/AgentOrchestrationService.java)
- [SynthesisAgent.java](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagent/src/main/java/com/redis/stockanalysisagent/agent/synthesisagent/SynthesisAgent.java)
- [ChatService.java](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagent/src/main/java/com/redis/stockanalysisagent/chat/ChatService.java)

If you want to understand the production capabilities, look at:

- memory:
  - [AmsChatMemoryRepository.java](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagent/src/main/java/com/redis/stockanalysisagent/memory/AmsChatMemoryRepository.java)
  - [LongTermMemoryAdvisor.java](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagent/src/main/java/com/redis/stockanalysisagent/memory/LongTermMemoryAdvisor.java)
- caching:
  - [ExternalDataCache.java](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagent/src/main/java/com/redis/stockanalysisagent/cache/ExternalDataCache.java)
  - [SemanticAnalysisCache.java](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagent/src/main/java/com/redis/stockanalysisagent/semanticcache/SemanticAnalysisCache.java)
- observability:
  - [OrchestrationObservability.java](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagent/src/main/java/com/redis/stockanalysisagent/observability/OrchestrationObservability.java)
- evaluation:
  - [SynthesisAgentEvaluationTests.java](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagent/src/test/java/com/redis/stockanalysisagent/evaluation/SynthesisAgentEvaluationTests.java)

## How This Relates to the Workshop

This module is the finished app.

If you want the guided learning path, go back to:

- [stockanalysisagentworkshop/README.md](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagentworkshop/README.md)

If you want the runnable end state, stay here.

That pairing is the whole point of the repository:

- the workshop teaches the system
- this module shows the completed version
