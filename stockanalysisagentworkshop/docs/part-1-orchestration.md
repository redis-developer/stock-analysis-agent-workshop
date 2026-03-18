# Part 1: Orchestration

## Learning Goal

Teach learners how to design a multi-agent system with Spring AI using:

- one config class per agent
- one service class per agent
- structured result objects with `FinishReason`
- explicit orchestration in application code
- controlled context passing between agents

## Runtime Shape

- `frontend-first`
- browser UI as the default entrypoint
- backend remains stateless for each request
- no Redis
- no semantic cache
- no long-term memory
- mock tools and mock data only

## Agents Included

- `CoordinatorAgent`
- `MarketDataAgent`
- `FundamentalsAgent`
- `SynthesisAgent`

This is the smallest useful set that still teaches:

- routing
- specialist agents
- explicit orchestration
- final composition

## What Learners Should Explicitly Implement

Learners should write:

- `CoordinatorAgentConfig`
- `CoordinatorAgent`
- `RoutingDecision`
- `ExecutionPlan`

- `MarketDataAgentConfig`
- `MarketDataAgent`
- `MarketDataResult`
- `MarketSnapshot`

- `FundamentalsAgentConfig`
- `FundamentalsAgent`
- `FundamentalsResult`
- `FundamentalsSnapshot`

- `SynthesisAgentConfig`
- `SynthesisAgent`
- `SynthesisResult`

- `AgentOrchestrationService`
- `AnalysisRequest`
- `AnalysisResponse`

## What Should Be Pre-Provided

Pre-provide:

- Spring Boot application bootstrap
- Gradle build
- frontend shell
- controller shell
- chat or request-response DTO shells
- package structure
- mock stock data service
- tool classes
- shared enums or thin DTO shells if they are not part of the learning goal

Learners should not spend time in Part 1 on:

- frontend implementation
- web controllers
- Redis configuration
- API clients
- cache setup
- retry logic
- observability

## Recommended Package Structure

```text
src/main/java/com/redis/stockanalysisagentworkshop/
  StockAnalysisAgentWorkshopApplication.java
  agent/
    coordinator/
      CoordinatorAgentConfig.java
      CoordinatorAgent.java
      RoutingDecision.java
      ExecutionPlan.java
    marketdata/
      MarketDataAgentConfig.java
      MarketDataAgent.java
      MarketDataResult.java
      MarketSnapshot.java
    fundamentals/
      FundamentalsAgentConfig.java
      FundamentalsAgent.java
      FundamentalsResult.java
      FundamentalsSnapshot.java
    synthesis/
      SynthesisAgentConfig.java
      SynthesisAgent.java
      SynthesisResult.java
    orchestration/
      AgentOrchestrationService.java
      AnalysisRequest.java
      AnalysisResponse.java
      AgentExecution.java
      AgentExecutionStatus.java
      AgentType.java
  chat/
    ChatService.java
    api/
      ChatController.java
      ChatRequest.java
      ChatResponse.java
  tool/
    MarketDataTools.java
    FundamentalsTools.java
  support/
    MockStockDataService.java
src/main/resources/static/
  index.html
  app.js
  app.css
```

## Control Flow

The intended execution model is:

1. user submits a stock question from the browser
2. coordinator returns a structured routing decision
3. orchestration decides what to run
4. selected specialist agents execute
5. synthesis combines outputs when needed
6. backend returns the final answer to the frontend

## Result Model

Each agent should follow the same pattern:

- `AgentConfig`
  - owns the `ChatClient`
  - sets system prompt, tools, and model behavior
- `Agent`
  - owns execution logic
  - calls the model
  - interprets `FinishReason`
- `AgentResult`
  - orchestration metadata
  - `finishReason`, `nextPrompt`, `finalResponse`
- nested domain contract
  - the structured payload passed downstream

This is the core lesson of Part 1.

## Validation

- `./gradlew test`
- `./gradlew bootRun`
- open the browser frontend
- prompt:
  - `Give me a full view on Apple with price and fundamentals`

Expected:

- coordinator selects relevant agents
- orchestration executes them in order
- final answer is synthesized
- frontend renders the response without any memory dependency

## Open Decisions

Before scaffolding the actual Part 1 files, agree on:

- whether the frontend should call a stateless `/api/chat` endpoint or a simpler `/api/analysis` endpoint in Part 1
- whether learners get all DTO shells pre-created
- whether `SynthesisAgent` belongs in Part 1 or is introduced at the end of it
