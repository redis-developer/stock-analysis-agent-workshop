# Part 2: Redis Agent Memory

## Learning Goal

Teach learners how to add conversation continuity without rewriting the agent system.

## New Concepts

- Spring AI `ChatMemory`
- `MessageChatMemoryAdvisor`
- Redis-backed short-term memory
- long-term memory through Redis Agent Memory
- memory injection through advisors

## What Changes From Part 1

Keep:

- the same agents
- the same orchestration
- the same contracts
- the same frontend entrypoint

Add:

- short-term conversation memory
- long-term memory retrieval and injection

## What Learners Should Explicitly Implement

Learners should write:

- memory config wiring
- `LongTermMemoryAdvisor`
- `AgentMemoryService`
- chat service that passes conversation ids

## What Should Be Pre-Provided

Pre-provide:

- Compose setup
- Redis and Agent Memory Server wiring shell
- any missing memory DTOs or config shells

## Recommended New Package Structure

```text
memory/
  AgentMemoryConfig.java
  AmsChatMemoryRepository.java
  LongTermMemoryAdvisor.java
  service/
    AgentMemoryService.java
chat/
  ChatService.java
  ChatRunner.java
  api/
    ChatController.java
    ChatRequest.java
    ChatResponse.java
```

## Key Teaching Point

Memory should be an architectural enhancement, not a rewrite of orchestration or the frontend surface.

## Validation

- `docker compose up -d redis agent-memory-server`
- `./gradlew bootRun`

Prompt sequence:

1. `What is the current price of Apple?`
2. `What about fundamentals?`

Expected:

- second turn reuses memory context
- agents and orchestration stay structurally the same
