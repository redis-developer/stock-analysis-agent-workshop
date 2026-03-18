# Part 5: Introducing Redis Agent Memory

Up to this point, the system you built can route requests, run specialist agents, and synthesize a final answer.

That is enough to understand multi-agent orchestration.

It is not enough to build a production-ready multi-agent system.

In a real application, users do not ask one perfectly self-contained question and disappear.

They ask follow-up questions such as:

- "What about Microsoft instead?"
- "Use the same time horizon as before."
- "I care more about long-term growth than short-term momentum."
- "Compare this with the company we just discussed."

If the system cannot remember anything across turns, the experience breaks down immediately.

This part introduces the memory model we will use next:

- Redis as the memory infrastructure
- Agent Memory Server as the memory API layer
- working memory for short-lived conversation state
- long-term memory for durable, retrievable context

## What Memory Solves in a Multi-Agent System

Memory is not there to make the system feel magical.

It is there to solve real application problems.

In a production multi-agent system, memory helps with:

- continuity across turns
- resolving omitted references
- preserving stable user preferences
- keeping agents focused without forcing the user to repeat everything

Without memory, the coordinator sees each message in isolation.

That makes the system fragile:

- the user has to restate the ticker
- the user has to restate the analysis goal
- preferences vanish between turns
- the app cannot build on previous context

With memory, the system can use prior context when it is helpful, while still keeping the current user message as the source of truth.

That last point matters.

Memory should support orchestration.

Memory should not silently override the current request.

## The Three Memory Problems

As soon as you add memory to an agent system, you have three separate problems to solve.

### 1. Memory Acquisition

What should be saved?

Examples:

- the conversation itself
- stable user preferences
- company-specific facts mentioned by the user
- prior analysis context that may matter later

If you save too little, continuity breaks.

If you save too much, memory becomes noisy and retrieval gets worse.

### 2. Memory Management

How should memory be organized over time?

Examples:

- what belongs in short-term memory
- what belongs in long-term memory
- how long short-term memory should live
- how long-term memory should be ranked or deleted

If you do not manage memory deliberately, it grows without boundaries and becomes less useful.

### 3. Memory Retrieval

What should come back into the prompt for the current request?

Examples:

- the recent turns in this session
- long-term memories relevant to the new question
- preferences that should influence the current interaction

This is one of the most important design points in a multi-agent system.

The goal is not "retrieve everything."

The goal is "retrieve only the context that helps this request."

## The Redis Agent Memory Server Model

The memory model from the Redis material breaks the system into clear layers:

```text
Redis
    |
    v
Agent Memory Server
    |
    +--> Working Memory
    |
    +--> Long-term Memory
```

This is useful because it keeps memory concerns separate from agent concerns.

Your agents do not need to know how memory is stored internally in Redis.

They interact with memory through application code and memory-aware Spring AI components.

## Working Memory vs Long-term Memory

The most important distinction is this one:

- working memory is for active conversation state
- long-term memory is for durable, retrievable context

### Working Memory

Working memory is short-lived session context.

It usually contains things like:

- namespace
- session ID
- user ID
- messages
- time-to-live
- current context

In this workshop, working memory is what lets the system preserve the recent conversation.

That means the app can remember things like:

- which ticker the user was just discussing
- what question was asked last
- how the current session has evolved

### Long-term Memory

Long-term memory is durable context that can be retrieved later.

The Redis model includes fields such as:

- topics
- text
- entities
- embeddings
- user ID
- type
- session ID
- access count

This is what lets the system recover useful older context even when it is no longer in the current message window.

That makes long-term memory useful for things like:

- stable user preferences
- repeated user interests
- durable facts worth retrieving later
- continuity across longer interactions

## How This Maps to Spring AI

In this application, memory is not a separate "AI trick."

It is wired into the normal Spring application structure.

### Working Memory in Spring AI

Working memory is integrated through `ChatMemory`.

In this project, that memory is backed by `AmsChatMemoryRepository`, which stores and retrieves conversation messages through the Agent Memory Server.

So the flow is:

- Spring AI asks for conversation memory
- `AmsChatMemoryRepository` talks to Agent Memory Server
- Agent Memory Server stores working memory in Redis

This gives us session continuity without making the agents themselves manage message history manually.

### Long-term Memory in Spring AI

Long-term memory is integrated through `LongTermMemoryAdvisor`.

That advisor:

- looks at the current user message
- searches long-term memory for relevant memories
- injects those memories into the current prompt as supplemental context

This matters because long-term memory retrieval should happen before the coordinator decides how to route the request.

That is exactly what advisors are good at:

- they modify the request before the model call
- they do it in a reusable, explicit way
- they keep memory retrieval outside the agent execution logic

## Why This Matters for Production-Ready Multi-Agent Systems

If you want a production-ready multi-agent system, memory cannot be an afterthought.

Without memory, your orchestration may be correct but the user experience will still feel broken.

Production readiness means the system can:

- handle follow-up questions
- preserve continuity
- reuse stable context carefully
- stay predictable when context changes across turns

That is why memory belongs in the architecture, not in a one-off prompt hack.

## Memory Integration Patterns

The Redis material highlights three integration patterns:

- LLM-driven
- code-driven
- background

### LLM-driven

In this pattern, the model decides what to store or retrieve.

This can work for chatbots, but it gives the model more control over application behavior.

### Code-driven

In this pattern, your application decides how memory is used.

This is the best fit for this workshop.

It keeps memory explicit and predictable:

- your code chooses where memory is read
- your code chooses where memory is written
- your code chooses which layer receives memory context

This is the same philosophy we already used for orchestration.

We keep the flow in code.

We do not hide application behavior inside the model.

### Background

In this pattern, memory extraction happens automatically outside the main interaction path.

This can be useful for systems that continuously learn from conversations over time.

For this workshop, it is useful to know this pattern exists, but our main focus is the code-driven approach.

## Memory Extraction Strategies

The Redis material also highlights four extraction strategies:

- discrete
- summary
- preferences
- custom

### Discrete

Extract individual facts and preferences.

This is a strong default when you want precise, retrievable memories.

### Summary

Store summaries of conversations instead of many small facts.

This can reduce noise, but it also compresses detail.

### Preferences

Focus on user preferences and characteristics.

This is especially useful when the system needs to preserve stable user tendencies across sessions.

### Custom

Use domain-specific extraction prompts.

This is the most important one for production multi-agent systems.

In a stock-analysis system, custom extraction is how you start making memory useful instead of generic.

For example, you may want memory to capture things like:

- preferred investment horizon
- risk tolerance
- favored sectors
- whether the user cares more about fundamentals or technicals

That is much more useful than storing generic conversation fragments.

## Forgetting Is Part of the Design

Production-ready memory is not only about storing things.

It is also about deciding what should stop being important.

The Redis material calls out two broad strategies:

- recency scoring
- hard forgetting

### Recency Scoring

This is soft decay.

Newer and more relevant memories rank higher than older ones.

That helps retrieval stay fresh without deleting everything immediately.

### Hard Forgetting

This is explicit deletion.

Examples:

- age-based expiration
- inactivity-based cleanup
- budget-based cleanup

This matters because long-term memory should not grow forever without rules.

Good memory systems are not only good at remembering.

They are also good at forgetting.

## How Memory Should Enter This System

For this workshop, memory should enter the system in a controlled way.

The most important rule is:

Use memory to support the current request, not to replace it.

In practice, that means:

- the current user message stays the source of truth
- working memory preserves the recent conversation
- long-term memory is retrieved as supplemental background
- the coordinator can use that background to resolve omitted references
- orchestration still stays in application code

That gives us a system that is both stateful and predictable.

## The Memory-Aware Flow

Once memory is introduced, the high-level flow becomes:

```text
User message
    |
    v
Chat layer
    |
    +--> Working memory lookup
    |
    +--> Long-term memory retrieval
    |
    v
Coordinator
    |
    v
Execution plan
    |
    v
Orchestrator
    |
    v
Specialist agents
    |
    v
Synthesis
    |
    v
Final answer
    |
    v
Working memory save
```

This is the important architectural idea:

Memory improves the inputs to the system.

It does not replace the orchestration layer.

## What You Should Take Away from This Part

Before you implement Redis memory, you should leave this part with three ideas:

1. multi-agent systems need memory if they are going to feel reliable across turns
2. memory has to solve acquisition, management, and retrieval separately
3. the production-ready way to add memory is to keep it explicit, bounded, and code-driven

In the next part, you will connect this memory model to the actual Spring AI application.
