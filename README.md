# Stock Analysis Agent

This repository teaches you how to build a production-style multi-agent system with Spring AI and Redis.

It contains both:

- a learner-facing workshop
- a finished reference implementation

If you are learning, start with the workshop.

If you want to see the end state, run or read the final app.

## Start Here

This repository has two modules:

- [stockanalysisagentworkshop](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagentworkshop)
  This is the workshop path. It is the best place to start if you want to learn how the system is built.

- [stockanalysisagent](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagent)
  This is the finished application. Use it as the reference implementation and runnable end state.

The workshop is not separate from the real app in spirit.

The idea of this repository is:

- learn the architecture in the workshop
- understand how the pieces fit together
- compare that with the finished application

## What You Will Learn

By working through this repository, you will learn how to build a multi-agent application that can:

- route a user request to the right specialist agents
- run explicit orchestration in application code
- synthesize a grounded final answer
- preserve conversation memory
- cache expensive or repeated work
- use advisor-based semantic caching for near-duplicate requests
- emit useful traces for observability
- evaluate answer quality with Spring AI

This is not a generic “AI demo” repository.

It is focused on one concrete system:

a stock-analysis assistant that combines:

- market data
- fundamentals
- news
- technical analysis

## The System You Are Building

At a high level, the application works like this:

```text
user request
    |
    v
coordinator
    |
    v
orchestrator
    |
    +--> market data agent
    +--> fundamentals agent
    +--> news agent
    +--> technical analysis agent
    |
    v
synthesis agent
    |
    v
final answer
```

As the repository progresses, that core flow is extended with:

- Redis-backed memory
- advisor-based semantic caching
- observability
- evaluation

So the goal is not just to “make several agents talk.”

The goal is to show what it takes to turn a multi-agent system into a production-style application.

## If You Are Following the Workshop

Go to:

- [stockanalysisagentworkshop/README.md](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagentworkshop/README.md)

That README is written for learners and walks you through the full sequence.

You will move through the workshop in order:

1. Spring AI basics
2. multi-agent system design
3. specialist agents
4. coordinator and orchestration
5. memory
6. caching
7. observability
8. evaluation
9. what comes next

The workshop is designed so you do not have to invent the whole system architecture yourself.

You will be told:

- what file to open
- what code to paste
- why that code matters
- what that code is doing in the multi-agent system

## If You Want to Run the Finished App

Go to:

- [stockanalysisagent/README.md](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagent/README.md)

The finished app is a Spring Boot application with a browser chat UI and a Redis-backed local stack.

From the repository root, the most useful commands are:

```bash
./gradlew :stockanalysisagent:bootRun
./gradlew :stockanalysisagent:test
./gradlew :stockanalysisagent:compileJava
```

## Local Setup

If you want to run the real application locally, you will usually want:

- Ollama
- Redis
- Agent Memory Server
- Redis Insight
- optionally Zipkin for tracing

The repository already includes a local Docker stack in:

- [compose.yaml](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/compose.yaml)

Start it with:

```bash
docker compose up -d redis ollama agent-memory-server agent-memory-task-worker redis-insight
```

If you want Compose to pre-load the default Ollama models into its own volume, run this once:

```bash
docker compose --profile ollama-setup up ollama-model-loader
```

If you also want tracing:

```bash
docker compose up -d zipkin
```

## Local Configuration

For local secrets and machine-specific settings, use:

- [/.env.example](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/.env.example)
- [application-local.properties.example](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/application-local.properties.example)

Typical local values include:

- `OLLAMA_BASE_URL`
- `OLLAMA_CHAT_MODEL`
- Twelve Data API key from [Twelve Data](https://twelvedata.com/docs)
- Tavily API key from [Tavily](https://app.tavily.com/home)
- SEC user agent
- Agent Memory Server URL

Embeddings are disabled by default in the repo now. If you want semantic cache or Redis long-term memory back, opt in with the commented embedding settings in [/.env.example](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/.env.example).

Both Spring Boot apps load `.env` and `application-local.*` automatically when those files exist in either the repository root or the module directory.

## Repository Map

- [stockanalysisagentworkshop](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagentworkshop)
  Learner-facing workshop materials and scaffolded code

- [stockanalysisagent](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagent)
  Finished application

- [compose.yaml](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/compose.yaml)
  Local Ollama, Redis, Agent Memory Server, Redis Insight, and Zipkin stack

- [docs](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/docs)
  Supporting planning and project documentation

## What Success Looks Like

If you finish this repository path, you should be able to explain and build:

- how Spring AI tools, prompts, and structured outputs work
- how specialist agents are structured
- how a coordinator decides what should run
- how orchestration stays explicit in code
- how Redis supports memory, caching, and scaling
- how observability helps you understand multi-agent execution
- how evaluation helps you measure answer quality

That is the real outcome of this repository.

Not just a demo that runs, but a clear mental model for how to build multi-agent systems that are useful, observable, and ready to grow.
