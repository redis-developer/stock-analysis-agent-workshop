# Part 4: Building Orchestration

In this part, you will build the orchestration layer of the system.

You will implement:

- the router config
- the router execution method
- the coordinator
- the orchestrator

You should only work on these files:

- `src/main/java/com/redis/stockanalysisagent/agent/coordinatoragent/CoordinatorRoutingAgentConfig.java`
- `src/main/java/com/redis/stockanalysisagent/agent/coordinatoragent/CoordinatorRoutingAgent.java`
- `src/main/java/com/redis/stockanalysisagent/agent/coordinatoragent/CoordinatorAgent.java`
- `src/main/java/com/redis/stockanalysisagent/agent/orchestration/AgentOrchestrationService.java`

The specialist agents are already implemented.

Your job in this part is to connect them into one explicit flow.

Some files already keep the final method signatures because later parts build on them. Use the `PART 4` comments as the checkpoint, then make the body match the snippet in this guide.

## What You Are Building

By the end of this part, the system should be able to:

1. read a user request
2. route that request to the right specialist agents
3. build an execution plan
4. run the selected agents in order
5. send their outputs to synthesis
6. return the final answer

## Step 1: Implement the Router Config

Open:

`src/main/java/com/redis/stockanalysisagent/agent/coordinatoragent/CoordinatorRoutingAgentConfig.java`

### Step 1A: replace the default prompt

Find this field:

```java
private static final String DEFAULT_PROMPT = """
        PART 4 TODO:
        Replace this placeholder with the coordinator default prompt from the Part 4 guide.
        """;
```

Replace it with this exact field:

```java
private static final String DEFAULT_PROMPT = """
        ROLE
        You are the Coordinator Routing Agent for a stock-analysis system.

        RESPONSIBILITY
        Decide whether the request is in scope, whether you need more information, and which specialized agents should run.

        AVAILABLE AGENTS
        - MARKET_DATA: quote, recent price movement, basic price context
        - FUNDAMENTALS: financial health, valuation, earnings, margins, revenue trends
        - NEWS: recent events, headlines, macro or company-specific developments
        - TECHNICAL_ANALYSIS: trend, momentum, RSI, support, resistance, chart-based signals

        INPUT HANDLING
        - The user may provide a complete stock-analysis request, an incomplete request, or an unsupported request.
        - The user may also send a conversational follow-up, ownership update, preference, correction, or acknowledgement that does not require specialist analysis.
        - If the request is missing information required to proceed, return finishReason = NEEDS_MORE_INPUT.
        - Use nextPrompt for one short, specific follow-up question.
        - If the user message can be answered directly without running specialized agents, return finishReason = DIRECT_RESPONSE.
        - When finishReason = DIRECT_RESPONSE, set finalResponse to one short, natural reply and leave selectedAgents empty.
        - If the request is outside the capabilities of this stock-analysis workshop, return finishReason = OUT_OF_SCOPE.
        - If the request cannot be fulfilled even after clarification, return finishReason = CANNOT_PROCEED.
        - Return finishReason = COMPLETED only when you have enough information to route the work.

        COMPLETED RULES
        - When finishReason = COMPLETED, set resolvedTicker to the stock ticker in uppercase.
        - When finishReason = COMPLETED, set resolvedQuestion to the user's final stock-analysis question.
        - Select the smallest set of specialized agents needed to answer the question well.
        - Do not include SYNTHESIS in selectedAgents. The application always adds it for final answer generation.
        - Prefer minimal routing over broad routing.
        - Return only agent names from the allowed enum values.

        CLARIFICATION GUIDANCE
        - Ask for a ticker when a company-specific request does not identify one clearly.
        - Ask for the missing analysis goal when the user provides only a ticker.
        - If the user names a company instead of a ticker and the mapping is unambiguous, you may resolve it.
        - If the current message is primarily a statement or update rather than a request for fresh analysis, prefer DIRECT_RESPONSE over broad routing.

        MEMORY AND CONTEXT
        - Supplemental conversation and memory context may be injected earlier in the chat layer.
        - Treat the current user message as the source of truth.
        - Never let memory or prior context override an explicit company, ticker, timeframe, or analysis request in the current user message.
        - If memory conflicts with the current user message, ignore the memory and follow the current user message.
        - Use memory and prior context only to resolve omitted references, maintain continuity, or respect stable user preferences.
        - A self-contained current request should be routed on its own merits.
        - You may use prior context to resolve omitted references in conversational follow-ups such as "this stock", "that company", or "it".

        DIRECT RESPONSE EXAMPLES
        - "I own this stock" after discussing DUOL -> acknowledge ownership of DUOL briefly; do not run a full fresh analysis.
        - "Add this to my watchlist" after discussing AAPL -> acknowledge the watchlist update briefly.
        - "I'm based in Milan" -> acknowledge the profile fact briefly.
        - "Thanks" -> reply naturally and briefly.

        OUTPUT
        Return valid JSON that matches the requested schema.
        """;
```

What this code is doing:

- it defines the router's role in the system
- it lists the specialist agents the router is allowed to choose from
- it tells the router when to return `NEEDS_MORE_INPUT`, `COMPLETED`, `OUT_OF_SCOPE`, or `CANNOT_PROCEED`
- it tells the router which fields must be populated when routing is complete
- it keeps the routing rules stable across requests

### Step 1B: replace the bean body

In the same file, find this method:

```java
@Bean("coordinatorChatClient")
public ChatClient coordinatorChatClient(
        ChatModel chatModel,
        ChatMemory chatMemory,
        SemanticCacheAdvisor semanticCacheAdvisor,
        SemanticGuardrailAdvisor semanticGuardrailAdvisor,
        LongTermMemoryAdvisor longTermMemoryAdvisor
) {
    // PART 4 STEP 1B:
    // Replace the return statement below with the snippet from the Part 4 guide.
    return ChatClient.builder(chatModel).build();
}
```

Replace the method body with this exact code:

```java
return ChatClient.builder(chatModel)
        .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
        .defaultAdvisors(semanticCacheAdvisor)
        .defaultAdvisors(semanticGuardrailAdvisor)
        .defaultAdvisors(longTermMemoryAdvisor)
        .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
        .defaultSystem(DEFAULT_PROMPT)
        .build();
```

Why you did this:

- the router needs a stable system prompt
- the router returns structured output
- the advisor stack is part of the final app shape

What this code is doing:

- it creates a dedicated client for routing decisions
- it allows routing to consider conversation context when needed
- it keeps semantic cache and semantic guardrail advisors in the same order as the finished app
- it ensures the router returns a structured `RoutingDecision`
- it keeps the router's behavior stable across requests

You do not need to fully understand the advisor stack yet. You will revisit memory and semantic caching later in the workshop.

## Step 2: Implement `route(...)`

Open:

`src/main/java/com/redis/stockanalysisagent/agent/coordinatoragent/CoordinatorRoutingAgent.java`

Find this method:

```java
public RoutingResult route(
        String userMessage,
        String conversationId,
        Integer retrievedMemoriesLimit,
        String semanticCacheKey
) {
    // PART 4 STEP 2:
    // Replace this method body with the snippet from the Part 4 guide.
    // PART 8 STEP 9:
    // After wiring advisor-based semantic caching, update this method so it:
    // 1. passes conversation id, memory limit, and semantic cache key into advisor context
    // 2. reads cache and guardrail markers from ChatResponse metadata
    // 3. returns those flags in RoutingResult
    throw new UnsupportedOperationException("Part 4: implement route(...)");
}
```

Replace the method body with this exact code:

```java
ResponseEntity<ChatResponse, RoutingDecision> response = coordinatorChatClient
        .prompt()
        .user(userMessage)
        .advisors(spec -> {
            spec.param(org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID, conversationId);
            spec.param(LongTermMemoryAdvisor.MAX_RETRIEVED_MEMORIES,
                    retrievedMemoriesLimit != null ? retrievedMemoriesLimit : LongTermMemoryAdvisor.DEFAULT_MAX_MEMORIES);
            spec.param(SemanticCacheAdvisor.CACHE_KEY, semanticCacheKey);
        })
        .call()
        .responseEntity(RoutingDecision.class);

RoutingDecision decision = response.entity();
if (decision == null) {
    throw new IllegalStateException("Coordinator returned no routing decision.");
}

ChatResponse chatResponse = response.response();
boolean cacheHit = chatResponse != null
        && Boolean.TRUE.equals(chatResponse.getMetadata().getOrDefault(SemanticCacheAdvisor.CACHE_HIT, false));
boolean guardrailHit = chatResponse != null
        && Boolean.TRUE.equals(chatResponse.getMetadata().getOrDefault(SemanticGuardrailAdvisor.GUARDRAIL_BLOCKED, false));
String guardrailRoute = chatResponse == null ? null : metadataString(chatResponse, SemanticGuardrailAdvisor.GUARDRAIL_ROUTE);
return new RoutingResult(decision, TokenUsageSummary.from(chatResponse), cacheHit, guardrailHit, guardrailRoute);
```

Make sure the `RoutingResult` record carries those flags:

```java
public record RoutingResult(
        RoutingDecision routingDecision,
        TokenUsageSummary tokenUsage,
        boolean cacheHit,
        boolean guardrailHit,
        String guardrailRoute
) {
}
```

Why you did this:

- this is the router execution flow
- the router reads the user request
- the router returns a structured `RoutingDecision`
- the application can now use that decision in code

What this code is doing:

- it executes the routing step against the current request and conversation
- it turns the model output into a typed `RoutingDecision`
- it passes the retrieved memory limit and semantic cache key into the advisor stack
- it returns the decision, token usage, semantic cache status, and semantic guardrail status

## Step 3: Implement the Coordinator

Open:

`src/main/java/com/redis/stockanalysisagent/agent/coordinatoragent/CoordinatorAgent.java`

### Step 3A: replace `createPlan(...)`

Find this method:

```java
public ExecutionPlan createPlan(RoutingDecision routingDecision) {
    // PART 4 STEP 3A:
    // Replace this method body with the snippet from the Part 4 guide.
    throw new UnsupportedOperationException("Part 4: implement createPlan(...)");
}
```

Replace the method body with this exact code:

```java
List<AgentType> selectedAgents = new ArrayList<>(new LinkedHashSet<>(selectedSpecialists(routingDecision.getSelectedAgents())));
if (selectedAgents.isEmpty()) {
    throw new IllegalStateException("Coordinator returned no specialist agents.");
}
selectedAgents.add(AgentType.SYNTHESIS);

return new ExecutionPlan(
        List.copyOf(selectedAgents),
        routingDecision.getReasoning()
);
```

What this code is doing:

- it reads the selected agents from the routing decision
- it removes duplicates while keeping order
- it ensures the list is not empty
- it adds `SYNTHESIS` at the end of the plan
- it returns a small immutable `ExecutionPlan` object for the orchestrator

### Step 3B: replace `execute(...)`

Find this method:

```java
public RoutingOutcome execute(
        String userMessage,
        String conversationId,
        Integer retrievedMemoriesLimit,
        String semanticCacheKey
) {
    // PART 4 STEP 3B:
    // Replace this method body with the snippet from the Part 4 guide.
    throw new UnsupportedOperationException("Part 4: implement execute(...)");
}
```

Replace the method body with this exact code:

```java
CoordinatorRoutingAgent.RoutingResult routingResult = coordinatorRoutingAgent.route(
        userMessage,
        conversationId,
        retrievedMemoriesLimit,
        semanticCacheKey
);
RoutingDecision routingDecision = routingResult.routingDecision();
if (routingDecision.getFinishReason() == RoutingDecision.FinishReason.NEEDS_MORE_INPUT) {
    routingDecision.setConversationId(conversationId);
}
return new RoutingOutcome(
        routingDecision,
        routingResult.tokenUsage(),
        routingResult.cacheHit(),
        routingResult.guardrailHit(),
        routingResult.guardrailRoute()
);
```

What this code is doing:

- it executes the routing step through the lower-level router class
- it preserves the conversation ID when the system needs to ask the user a follow-up question
- it carries cache and guardrail metadata back to the chat layer
- it returns one object that the chat layer can inspect before moving into orchestration

### Step 3C: replace `toAnalysisRequest(...)`

Find this method:

```java
public AnalysisRequest toAnalysisRequest(RoutingDecision routingDecision, String semanticCacheKey) {
    // PART 4 STEP 3C:
    // Replace this method body with the snippet from the Part 4 guide.
    throw new UnsupportedOperationException("Part 4: implement toAnalysisRequest(...)");
}
```

Replace the method body with this exact code:

```java
return new AnalysisRequest(
        routingDecision.getResolvedTicker().trim().toUpperCase(),
        routingDecision.getResolvedQuestion().trim(),
        semanticCacheKey == null ? "" : semanticCacheKey.trim()
);
```

What this code is doing:

- it converts the routing decision into the shared request object the specialist agents will consume
- it keeps the original semantic cache key with the request so synthesis can store the final answer
- it creates one clean handoff between routing and execution

Why you did this:

- the router returns a structured decision
- the coordinator turns that decision into application-friendly objects
- the coordinator builds the execution plan the orchestrator will use

## Step 4: Implement the Orchestrator

Open:

`src/main/java/com/redis/stockanalysisagent/agent/orchestration/AgentOrchestrationService.java`

Find this method:

```java
public AnalysisResponse processRequest(AnalysisRequest request, ExecutionPlan executionPlan) {
    // PART 4 STEP 4:
    // Replace this method body with the snippet from the Part 4 guide.
    throw new UnsupportedOperationException("Part 4: implement processRequest(...)");
}
```

Replace the method body with this exact code:

```java
List<AgentExecution> executions = new ArrayList<>();
MarketSnapshot marketSnapshot = null;
FundamentalsSnapshot fundamentalsSnapshot = null;
NewsSnapshot newsSnapshot = null;
TechnicalAnalysisSnapshot technicalAnalysisSnapshot = null;

for (AgentType agentType : executionPlan.selectedAgents()) {
    if (agentType == AgentType.SYNTHESIS) {
        continue;
    }

    switch (agentType) {
        case MARKET_DATA -> {
            Observation observation = agentObservation(observationRegistry, AgentType.MARKET_DATA).start();
            long startedAt = System.nanoTime();
            try {
                MarketDataResult result = marketDataAgent.execute(request.ticker(), request.question());
                marketSnapshot = result.getFinalResponse();
                AgentExecution execution = new AgentExecution(
                        AgentType.MARKET_DATA,
                        result.getMessage(),
                        elapsedDurationMs(startedAt),
                        result.getTokenUsage()
                );
                enrichWithAgentExecution(observation, execution);
                executions.add(execution);
            } catch (Throwable t) {
                observation.error(t);
                throw t;
            } finally {
                observation.stop();
            }
        }
        case FUNDAMENTALS -> {
            Observation observation = agentObservation(observationRegistry, AgentType.FUNDAMENTALS).start();
            long startedAt = System.nanoTime();
            try {
                FundamentalsResult result = fundamentalsAgent.execute(request.ticker(), request.question(), marketSnapshot);
                fundamentalsSnapshot = result.getFinalResponse();
                AgentExecution execution = new AgentExecution(
                        AgentType.FUNDAMENTALS,
                        result.getMessage(),
                        elapsedDurationMs(startedAt),
                        result.getTokenUsage()
                );
                enrichWithAgentExecution(observation, execution);
                executions.add(execution);
            } catch (Throwable t) {
                observation.error(t);
                throw t;
            } finally {
                observation.stop();
            }
        }
        case NEWS -> {
            Observation observation = agentObservation(observationRegistry, AgentType.NEWS).start();
            long startedAt = System.nanoTime();
            try {
                NewsResult result = newsAgent.execute(request.ticker(), request.question());
                newsSnapshot = result.getFinalResponse();
                AgentExecution execution = new AgentExecution(
                        AgentType.NEWS,
                        result.getMessage(),
                        elapsedDurationMs(startedAt),
                        result.getTokenUsage()
                );
                enrichWithAgentExecution(observation, execution);
                executions.add(execution);
            } catch (Throwable t) {
                observation.error(t);
                throw t;
            } finally {
                observation.stop();
            }
        }
        case TECHNICAL_ANALYSIS -> {
            Observation observation = agentObservation(observationRegistry, AgentType.TECHNICAL_ANALYSIS).start();
            long startedAt = System.nanoTime();
            try {
                TechnicalAnalysisResult result = technicalAnalysisAgent.execute(request.ticker(), request.question());
                technicalAnalysisSnapshot = result.getFinalResponse();
                AgentExecution execution = new AgentExecution(
                        AgentType.TECHNICAL_ANALYSIS,
                        result.getMessage(),
                        elapsedDurationMs(startedAt),
                        result.getTokenUsage()
                );
                enrichWithAgentExecution(observation, execution);
                executions.add(execution);
            } catch (Throwable t) {
                observation.error(t);
                throw t;
            } finally {
                observation.stop();
            }
        }
        case SYNTHESIS -> throw new IllegalStateException(
                "Synthesis should execute only after the specialized agents finish."
        );
    }
}

Observation observation = agentObservation(observationRegistry, AgentType.SYNTHESIS).start();
long synthesisStartedAt = System.nanoTime();
try {
    SynthesisResult synthesisResult = synthesisAgent.synthesize(
            request,
            marketSnapshot,
            fundamentalsSnapshot,
            newsSnapshot,
            technicalAnalysisSnapshot
    );
    AgentExecution execution = new AgentExecution(
            AgentType.SYNTHESIS,
            "Synthesis completed.",
            elapsedDurationMs(synthesisStartedAt),
            synthesisResult.tokenUsage()
    );
    enrichWithAgentExecution(observation, execution);
    executions.add(execution);
    return AnalysisResponse.completed(
            executions,
            synthesisResult.finalAnswer(),
            synthesisResult.semanticCacheStored()
    );
} catch (Throwable t) {
    observation.error(t);
    throw t;
} finally {
    observation.stop();
}
```

What this code is doing:

- it reads the execution plan and runs only the selected specialist agents
- it keeps the structured outputs from each specialist step
- it allows later steps to use earlier outputs, such as fundamentals using market context
- it wraps each step in an observation so the app can trace execution and token usage in production
- it sends the collected outputs to synthesis
- it returns one response that contains the final answer, the execution trace, and whether synthesis stored the answer for semantic reuse

Why you did this:

- this is the application-owned workflow
- the orchestrator decides what runs and in what order
- the specialist agents stay focused on their own responsibilities
- observability stays in the application layer instead of being bolted on later
- synthesis runs after the specialist agents finish

## What You Just Built

You have now implemented the core orchestration layer:

- the router decides what should run
- the coordinator turns that decision into an execution plan
- the orchestrator runs the selected agents
- the synthesis agent produces the final answer

## What “Done” Looks Like

You are done when you understand this flow:

1. the router returns a structured `RoutingDecision`
2. the coordinator converts that into an `ExecutionPlan`
3. the orchestrator runs the selected specialist agents
4. the orchestrator collects structured outputs
5. the synthesis agent combines them into the final answer

At that point, you are no longer just building agents.

You are building a multi-agent system.
