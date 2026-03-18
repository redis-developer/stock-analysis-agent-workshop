package com.redis.stockanalysisagent.observability;

import com.redis.stockanalysisagent.agent.coordinatoragent.RoutingDecision;
import com.redis.stockanalysisagent.agent.orchestration.AgentExecution;
import com.redis.stockanalysisagent.agent.orchestration.AgentType;
import com.redis.stockanalysisagent.agent.orchestration.TokenUsageSummary;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

public final class OrchestrationObservability {

    private OrchestrationObservability() {
    }

    // --- Observation names (become span names in Zipkin) ---

    public static final String CHAT_OBSERVATION = "stock-analysis.chat";
    public static final String COORDINATOR_OBSERVATION = "stock-analysis.coordinator";
    public static final String AGENT_OBSERVATION = "stock-analysis.agent";

    // --- Low cardinality keys (bounded values, indexed and filterable in Zipkin) ---

    public static final String KEY_FINISH_REASON = "orchestration.finish_reason";
    public static final String KEY_AGENT_TYPE = "orchestration.agent_type";
    public static final String KEY_AGENT_STATUS = "orchestration.agent_status";
    public static final String KEY_AGENT_COUNT = "orchestration.agent_count";
    public static final String KEY_SEMANTIC_CACHE_HIT = "orchestration.semantic_cache_hit";

    // --- High cardinality keys (unbounded values, visible in span detail view) ---

    public static final String KEY_TICKER = "orchestration.ticker";
    public static final String KEY_ROUTING_REASONING = "orchestration.routing_reasoning";
    public static final String KEY_SELECTED_AGENTS = "orchestration.selected_agents";
    public static final String KEY_PROMPT_TOKENS = "orchestration.prompt_tokens";
    public static final String KEY_COMPLETION_TOKENS = "orchestration.completion_tokens";
    public static final String KEY_TOTAL_TOKENS = "orchestration.total_tokens";
    public static final String KEY_DURATION_MS = "orchestration.duration_ms";
    public static final String KEY_AGENT_SUMMARY = "orchestration.agent_summary";
    public static final String KEY_CONVERSATION_ID = "orchestration.conversation_id";

    // --- Observation factory methods ---

    public static Observation chatObservation(ObservationRegistry registry) {
        return Observation.createNotStarted(CHAT_OBSERVATION, registry);
    }

    public static Observation coordinatorObservation(ObservationRegistry registry) {
        return Observation.createNotStarted(COORDINATOR_OBSERVATION, registry);
    }

    public static Observation agentObservation(ObservationRegistry registry, AgentType agentType) {
        return Observation.createNotStarted(AGENT_OBSERVATION, registry)
                .lowCardinalityKeyValue(KEY_AGENT_TYPE, agentType.name());
    }

    // --- Enrichment helpers ---

    public static void enrichWithRoutingDecision(Observation observation, RoutingDecision decision) {
        if (decision == null) {
            return;
        }

        observation.lowCardinalityKeyValue(KEY_FINISH_REASON, decision.getFinishReason().name());

        if (decision.getResolvedTicker() != null) {
            observation.highCardinalityKeyValue(KEY_TICKER, decision.getResolvedTicker().toUpperCase());
        }
        if (decision.getReasoning() != null) {
            observation.highCardinalityKeyValue(KEY_ROUTING_REASONING, decision.getReasoning());
        }
        if (decision.getSelectedAgents() != null) {
            observation.lowCardinalityKeyValue(KEY_AGENT_COUNT,
                    String.valueOf(decision.getSelectedAgents().size()));
            observation.highCardinalityKeyValue(KEY_SELECTED_AGENTS,
                    decision.getSelectedAgents().stream().map(AgentType::name).toList().toString());
        }
    }

    public static void enrichWithTokenUsage(Observation observation, TokenUsageSummary tokenUsage) {
        if (tokenUsage == null) {
            return;
        }
        if (tokenUsage.promptTokens() != null) {
            observation.highCardinalityKeyValue(KEY_PROMPT_TOKENS, String.valueOf(tokenUsage.promptTokens()));
        }
        if (tokenUsage.completionTokens() != null) {
            observation.highCardinalityKeyValue(KEY_COMPLETION_TOKENS, String.valueOf(tokenUsage.completionTokens()));
        }
        if (tokenUsage.totalTokens() != null) {
            observation.highCardinalityKeyValue(KEY_TOTAL_TOKENS, String.valueOf(tokenUsage.totalTokens()));
        }
    }

    public static void enrichWithAgentExecution(Observation observation, AgentExecution execution) {
        if (execution == null) {
            return;
        }
        observation.lowCardinalityKeyValue(KEY_AGENT_STATUS, execution.status().name());
        observation.highCardinalityKeyValue(KEY_DURATION_MS, String.valueOf(execution.durationMs()));
        if (execution.summary() != null) {
            observation.highCardinalityKeyValue(KEY_AGENT_SUMMARY, execution.summary());
        }
        enrichWithTokenUsage(observation, execution.tokenUsage());
    }
}
