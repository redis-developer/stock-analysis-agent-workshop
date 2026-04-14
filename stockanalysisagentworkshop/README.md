# Stock Analysis Agent Workshop

This workshop is for learning how to build a production-style multi-agent system with Spring AI and Redis.

By the end of it, you will have built and understood a system that can:

- route a user request
- run specialist agents
- synthesize a final answer
- remember prior context
- cache useful work
- emit traces for observability
- evaluate answer quality

You are not expected to invent the whole architecture yourself.

The workshop is designed so you can learn the important ideas one step at a time, with the codebase already scaffolded around you.

## How to Use This Workshop

Move through the parts in order.

Some parts are conceptual:

- they explain how the system works
- they introduce Spring AI and Redis concepts
- they prepare you for the coding step that comes next

Some parts are hands-on:

- they tell you exactly which file to open
- they tell you exactly which snippet to paste
- they explain why that code matters
- they explain what that code is doing for the multi-agent system

That means you should treat the workshop like a build sequence, not like reference documentation.

## What You Will Build

The system you build has this high-level shape:

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

As the workshop continues, you will add:

- memory
- semantic caching
- observability
- evaluation

So the system gradually moves from:

- "a working multi-agent demo"

to:

- "a production-style multi-agent application"

## What You Will Edit

You will not build every class in the application from scratch.

That is intentional.

The goal of the workshop is to help you understand:

- the architecture
- the Spring AI abstractions
- the Redis integration patterns
- the multi-agent orchestration flow

That means some parts of the system are already implemented for you, while selected files are left incomplete so you can build the pieces that matter most.

## Workshop Parts

### Foundations

- [Part 0: Spring AI Intro](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagentworkshop/docs/part-0-spring-ai-intro.md)
  Learn the Spring AI basics you need for the rest of the workshop: `ChatModel`, `ChatClient`, tools, memory, structured output, and advisors.

- [Part 1: Designing Multi-Agent Systems](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagentworkshop/docs/part-1-designing-multi-agent-systems.md)
  Learn the difference between single-agent and multi-agent systems, and understand the architecture you are about to build.

- [Part 2: Implementing Your First Agent](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagentworkshop/docs/part-2-implementing-your-first-agent.md)
  Build one specialist agent so you understand the core Spring AI agent pattern.

- [Part 3: Understanding Coordinator and Orchestration](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagentworkshop/docs/part-3-understanding-coordinator-and-orchestration.md)
  Learn how routing decisions, execution plans, and orchestration fit together before you implement them.

- [Part 4: Building Orchestration](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagentworkshop/docs/part-4-building-orchestration.md)
  Build the coordinator and orchestration layer that makes the whole multi-agent system run.

### Production Capabilities

- [Part 5: Introducing Redis Agent Memory](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagentworkshop/docs/part-5-introducing-redis-agent-memory.md)
  Learn why production-ready multi-agent systems need memory, and how Redis Agent Memory Server fits into the architecture.

- [Part 6: Building Memory Integration](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagentworkshop/docs/part-6-building-memory-integration.md)
  Build the `ChatMemoryRepository` and advisor integration that bring working memory and long-term memory into the app.

- [Part 7: Scaling with Semantic Caching](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagentworkshop/docs/part-7-scaling-with-semantic-caching.md)
  Learn how advisor-based semantic caching can short circuit repeated requests before the coordinator flow continues.

- [Part 8: Building Semantic Caching](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagentworkshop/docs/part-8-building-semantic-caching.md)
  Build the Redis-backed cache layers used by the application.

- [Part 9: Observability](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagentworkshop/docs/part-9-observability.md)
  Learn how to observe a multi-agent system with tracing and orchestration-aware spans.

- [Part 10: Introducing Evaluation](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagentworkshop/docs/part-10-introducing-evaluation.md)
  Learn why evaluation matters, what "LLM as a judge" means, and which native evaluation abstractions Spring AI provides.

- [Part 11: Building Evaluation](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagentworkshop/docs/part-11-building-evaluation.md)
  Build one real evaluation test class so you understand how to judge agent quality with Spring AI.

- [Part 12: What's Next](/Users/raphaeldelio/Documents/GitHub/stock-analysis-agent/stockanalysisagentworkshop/docs/part-12-whats-next.md)
  See how this architecture can grow into richer agents, nested workflows, Redis-backed rate limiting, and resumable workflows.

## What Success Looks Like

If you finish this workshop, you should be able to explain and implement:

- how Spring AI tools, prompts, and structured outputs work
- how to separate agent config from agent execution
- how a coordinator decides what should run
- how an orchestrator executes that decision
- how memory, caching, and observability make the system production-ready
- how evaluation helps you measure answer quality

That is the real goal.

Not just to copy code, but to understand how to build this kind of system yourself.
