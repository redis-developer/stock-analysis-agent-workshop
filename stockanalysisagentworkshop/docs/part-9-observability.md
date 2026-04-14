# Part 9: Observability

Multi-agent systems are harder to debug than single-agent ones. When a request fans out to four agents, each making LLM and tool calls, you need to see the full picture: what was decided, what ran, what failed, how long it took, and what it cost.

This part adds distributed tracing with Zipkin and then layers custom orchestration spans on top, so every trace tells the full story of a multi-agent request.

By the end of this part, you should be comfortable with these ideas:

- distributed tracing with Zipkin
- Spring AI auto-instrumented spans
- Micrometer's `Observation` API
- low cardinality vs high cardinality span attributes
- custom spans for orchestration decisions and per-agent execution

## The Problem

Spring AI auto-instruments LLM calls (`chat gpt-4o`) and tool calls (`tool_call getmarketsnapshot`). These tell us **what the model did**, but not **what the orchestration decided**:

- Which agents did the coordinator select, and why?
- Did each agent succeed or fail?
- How many tokens did each agent consume?
- Was the response served from cache?

Auto-instrumentation gives you **model observability**. Custom spans give you **orchestration observability**. For multi-agent systems, you need both.

## 1. Add Zipkin Tracing

This step requires no application code changes. Spring Boot auto-configures tracing for all Spring AI components once the right dependencies are on the classpath.

### Dependencies

Add these to `build.gradle.kts`:

```kotlin
// Observability — distributed tracing with Zipkin
implementation("org.springframework.boot:spring-boot-starter-actuator")
implementation("org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry")
implementation("io.micrometer:micrometer-tracing-bridge-otel")
implementation("org.springframework.boot:spring-boot-zipkin")
implementation("io.opentelemetry:opentelemetry-exporter-zipkin")
```

| Dependency | Purpose |
|------------|---------|
| `spring-boot-starter-actuator` | Enables `/actuator` endpoints |
| `spring-boot-micrometer-tracing-opentelemetry` | Spring Boot 4.0 auto-configuration that creates the `Tracer` bean |
| `micrometer-tracing-bridge-otel` | Bridges Micrometer tracing to OpenTelemetry |
| `spring-boot-zipkin` | Spring Boot 4.0 auto-configuration for the Zipkin exporter |
| `opentelemetry-exporter-zipkin` | Sends traces to Zipkin over HTTP |

### Configuration

Add to `application.yaml`:

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
    export:
      zipkin:
        endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}
  endpoints:
    web:
      exposure:
        include: "*"
```

Enable Spring AI observation logging:

```yaml
spring:
  ai:
    chat:
      client:
        observations:
          log-prompt: true
          log-completion: true
      observations:
        log-prompt: true
        log-completion: true
    tools:
      observations:
        include-content: true
```

### Docker Compose

Add the Zipkin service to `compose.yaml`:

```yaml
  zipkin:
    image: openzipkin/zipkin:latest
    container_name: stock-analysis-agent-zipkin
    ports:
      - "9411:9411"
```

### What You Get

Start everything and send a chat message. Open http://localhost:9411/zipkin/ and click **Run Query**.

You'll see auto-instrumented spans from Spring AI:

```
http post /api/chat                    [Spring Boot auto]
  chat gpt-4o                          [Spring AI auto — coordinator LLM call]
  chat gpt-4o                          [Spring AI auto — agent LLM call]
    tool_call getmarketsnapshot        [Spring AI auto — tool execution]
  chat gpt-4o                          [Spring AI auto — synthesis LLM call]
```

Each `chat gpt-4o` span carries `gen_ai.usage.input_tokens` and `gen_ai.usage.output_tokens`. Each `tool_call` span shows the tool arguments.

But you cannot tell from these spans which agent ran, why the coordinator chose it, or whether it succeeded.

## 2. Add the Orchestration Observability Utility

Copy `OrchestrationObservability.java` into `observability/`. This is a pre-built utility that centralizes all custom observation logic. It uses Micrometer's `Observation` API — the same API that Spring AI uses internally — so custom spans export to Zipkin alongside the auto-instrumented ones.

The utility has three concerns:

**Factory methods** — create observations with the right span names:

```java
chatObservation(registry)                        // stock-analysis.chat
coordinatorObservation(registry)                 // stock-analysis.coordinator
agentObservation(registry, AgentType.NEWS)       // stock-analysis.agent
```

**Key constants** — low cardinality (filterable) vs high cardinality (detail only):

| Type | Key | Values | Use Case |
|------|-----|--------|----------|
| Low | `agent_type` | MARKET_DATA, NEWS, ... | "Show me all NEWS spans" |
| Low | `agent_status` | COMPLETED, FAILED | "Show me failures" |
| Low | `finish_reason` | COMPLETED, OUT_OF_SCOPE | "How often does the coordinator reject?" |
| High | `ticker` | AAPL, NVDA, ... | What stock was analyzed? |
| High | `prompt_tokens` | 1247 | How much did this span cost? |
| High | `routing_reasoning` | "User asked for..." | Why did the coordinator route this way? |

**Enrichment helpers** — attach results to spans after execution:

```java
enrichWithRoutingDecision(observation, routingDecision);
enrichWithTokenUsage(observation, tokenUsageSummary);
enrichWithAgentExecution(observation, agentExecution);
```

## 3. Wire the Coordinator Observation

Wrap the coordinator routing call in a `stock-analysis.coordinator` span so Zipkin shows **what the coordinator decided**.

The change is in `ChatAnalysisService.java`:

```diff
+import io.micrometer.observation.Observation;
+import io.micrometer.observation.ObservationRegistry;
+import static com.redis.stockanalysisagent.observability.OrchestrationObservability.*;

 @Service
 class ChatAnalysisService {

+    private final ObservationRegistry observationRegistry;

     // inject via constructor...

     AnalysisTurn analyze(String request, String conversationId) {
-        CoordinatorAgent.RoutingOutcome routingOutcome = coordinatorAgent.execute(request, conversationId);
+        CoordinatorAgent.RoutingOutcome routingOutcome = observeCoordinator(request, conversationId);
         // ... rest unchanged ...
     }

+    private CoordinatorAgent.RoutingOutcome observeCoordinator(String request, String conversationId) {
+        Observation observation = coordinatorObservation(observationRegistry)
+                .highCardinalityKeyValue(KEY_CONVERSATION_ID, conversationId)
+                .start();
+        try {
+            CoordinatorAgent.RoutingOutcome outcome = coordinatorAgent.execute(request, conversationId);
+            enrichWithRoutingDecision(observation, outcome.routingDecision());
+            enrichWithTokenUsage(observation, outcome.tokenUsage());
+            observation.lowCardinalityKeyValue(KEY_SEMANTIC_CACHE_HIT, String.valueOf(outcome.fromSemanticCache()));
+            return outcome;
+        } catch (Throwable t) {
+            observation.error(t);
+            throw t;
+        } finally {
+            observation.stop();
+        }
+    }
```

The pattern:

```
start() -> execute -> enrich with results -> stop()
               |  (on error)
          error(t) -> rethrow -> stop()
```

- `start()` opens the span and starts the clock
- Enrichment happens **after** execution but **before** stop
- `KEY_SEMANTIC_CACHE_HIT` is a low-cardinality tag, so cache hit or miss can be filtered in Zipkin and metrics
- `error(t)` records the exception so Zipkin marks the span red
- `stop()` always runs in the finally block

### Verifying the Coordinator Span

In Zipkin, set filters: serviceName = `stock-analysis-agent`, spanName = `stock-analysis.coordinator`. Click **Run Query**.

The trace list shows root spans (`http post /api/chat`). Click one to open the waterfall. Find the `stock-analysis.coordinator` bar and click it. The tags panel shows:

```
orchestration.finish_reason      = COMPLETED
orchestration.agent_count        = 1
orchestration.ticker             = AAPL
orchestration.selected_agents    = [MARKET_DATA]
orchestration.routing_reasoning  = The user has asked for the current price...
orchestration.prompt_tokens      = 1000
orchestration.completion_tokens  = 94
orchestration.semantic_cache_hit = false
```

Try an out-of-scope request like "Tell me a joke". The coordinator span shows `finish_reason=OUT_OF_SCOPE` and no agent spans appear.

## 4. Add the Semantic Cache Execution Step

Zipkin shows the embedding and LLM spans, but the chat response payload should also show whether the semantic cache hit or missed.

In `ChatAnalysisService.java`, replace the Part 9 comment in `analyze(...)` with:

```java
executionSteps.add(cacheStep(routingOutcome.fromSemanticCache()));
```

Then replace the placeholder `cacheStep(...)` body with:

```java
return new ChatExecutionStep(
        SEMANTIC_CACHE,
        "Semantic cache",
        KIND_SYSTEM,
        0,
        cacheHit
                ? "Found a reusable response in the semantic cache and returned it through the coordinator."
                : "Checked the semantic cache before coordinator routing and found no reusable response.",
        null
);
```

Why you did this:

- the chat UI should make semantic cache hits visible without opening Zipkin
- cache hit or miss is part of the request story, not just a background optimization

What this code is doing:

- it adds a system execution step before the coordinator step
- it shows whether the semantic cache short-circuited the request or missed and allowed normal routing

## 5. Wire Per-Agent Observations

Each specialist agent method gets the same observation wrapper. The change is in `AgentOrchestrationService.java`:

```diff
+import io.micrometer.observation.Observation;
+import io.micrometer.observation.ObservationRegistry;
+import static com.redis.stockanalysisagent.observability.OrchestrationObservability.*;

 @Service
 public class AgentOrchestrationService {

+    private final ObservationRegistry observationRegistry;
     // inject via constructor...

     private void executeMarketData(AnalysisRequest request, AgentExecutionState state) {
+        Observation observation = agentObservation(observationRegistry, AgentType.MARKET_DATA).start();
         long startedAt = System.nanoTime();
         try {
             MarketDataResult result = marketDataAgent.execute(...);
-            state.addExecution(new AgentExecution(AgentType.MARKET_DATA, ...));
+            AgentExecution execution = new AgentExecution(AgentType.MARKET_DATA, ...);
+            enrichWithAgentExecution(observation, execution);
+            state.addExecution(execution);
             state.putStructuredOutput(AgentType.MARKET_DATA, result.getFinalResponse());
+        } catch (Throwable t) {
+            observation.error(t);
+            throw t;
+        } finally {
+            observation.stop();
         }
     }
```

The same pattern is applied to `executeFundamentals`, `executeNews`, and `executeTechnicalAnalysis`. The only difference is the `AgentType` enum value.

### What Shows Up in Zipkin

Each agent now appears as its own `stock-analysis.agent` span:

| Tag | Example | Use Case |
|-----|---------|----------|
| `agent_type` | MARKET_DATA | "Show me all NEWS spans" |
| `agent_status` | COMPLETED / FAILED | "Show me failures" |
| `prompt_tokens` | 800 | "Which agent costs most?" |
| `agent_summary` | "Processed latest quote..." | What did it produce? |

### What's Inside the Trace Now

| Span | Duration | What It Is |
|------|----------|------------|
| `http post /api/chat` | 16.8s | Root — the HTTP request |
| `embedding text-embedding-3-small` | 0.9s | Semantic cache check |
| `stock-analysis.coordinator` | 3.9s | Routing decision (Step 3) |
| `stock-analysis.agent` [MARKET_DATA] | 2.6s | Per-agent span (Step 5) |
| `stock-analysis.agent` [NEWS] | 6.4s | Per-agent span |
| `stock-analysis.agent` [TECHNICAL_ANALYSIS] | 2.8s | Per-agent span |
| `stock-analysis.agent` [FUNDAMENTALS] | 5.8s | Per-agent span |
| `chat gpt-4o` (synthesis) | 3.3s | Final synthesis LLM call |
| `embedding text-embedding-3-small` | 0.2s | Semantic cache write |

## 6. Reading a Real Trace

This is a full analysis of IBM, 16.8 seconds end to end. It starts with a semantic cache embedding check (854ms) — a cache miss, so we proceed into the coordinator flow. The coordinator takes 3.9 seconds and decides to dispatch all 4 agents with synthesis, spending 1,417 tokens on that routing decision. Then three agents — Market Data, News, and Technical Analysis — all kick off at exactly the same offset (+4.9s), which is why you see their bars overlapping in the waterfall: that's real parallelism, not a diagram. Market Data finishes in 2.6s, Technical Analysis in 2.8s, but News takes 6.4s — it's the bottleneck among the parallel group. Fundamentals starts *later* (+7.5s) because it depends on the Market Data result — you can see the gap. It then runs for 5.8 seconds including an SEC EDGAR lookup. Once all four agents complete, the Synthesis agent combines their outputs in a final 3.3s LLM call (873 tokens). The trace ends with a cache write embedding (170ms) so the next similar question gets a cache hit. On a cache hit, the advisor would return before long-term memory retrieval or coordinator routing. Total cost across the whole request: roughly 8,400 tokens spread across 6 LLM calls, and you can see exactly which agent consumed what.

## Navigating the Zipkin UI

1. **Query**: serviceName = `stock-analysis-agent`, spanName = `stock-analysis.coordinator` (or `stock-analysis.agent`). Click **Run Query**.
2. **Trace list**: Shows root spans (`http post /api/chat`) — click one to open the waterfall.
3. **Waterfall**: Find `stock-analysis.coordinator` and `stock-analysis.agent` bars — click any to inspect.
4. **Tags panel**: All orchestration metadata is here — ticker, agents, tokens, reasoning, status.
5. **Filter by agent type**: Add a tag filter `orchestration.agent_type=NEWS` to show only traces where the News agent ran.

## What You Should Be Comfortable With After Part 9

Before moving on, make sure these ideas feel clear:

- Spring AI auto-instruments LLM and tool calls, but not orchestration decisions
- Micrometer's `Observation` API lets you add custom spans alongside the auto-instrumented ones
- Low cardinality attributes are filterable (agent type, status, finish reason)
- Semantic cache hit or miss can be surfaced both in Zipkin tags and in the chat execution steps
- High cardinality attributes carry diagnostic detail (ticker, reasoning, token counts)
- The pattern is always: `start()` -> execute -> enrich -> `stop()` (with `error(t)` in the catch)
- The Zipkin waterfall shows the full request lifecycle — coordinator routing, agent execution, LLM calls, tool calls, cache operations — all in one view

If those ideas feel natural, you understand how to make a multi-agent system observable.
