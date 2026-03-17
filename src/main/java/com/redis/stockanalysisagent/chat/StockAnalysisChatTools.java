package com.redis.stockanalysisagent.chat;

import com.redis.stockanalysisagent.agent.orchestration.AgentExecution;
import com.redis.stockanalysisagent.agent.orchestration.AgentExecutionStatus;
import com.redis.stockanalysisagent.agent.orchestration.AgentOrchestrationService;
import com.redis.stockanalysisagent.agent.orchestration.AgentType;
import com.redis.stockanalysisagent.agent.orchestration.AnalysisRequest;
import com.redis.stockanalysisagent.agent.orchestration.AnalysisResponse;
import com.redis.stockanalysisagent.agent.coordinatoragent.CoordinatorAgent;
import com.redis.stockanalysisagent.agent.coordinatoragent.RoutingDecision;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class StockAnalysisChatTools {

    private static final String KIND_SYSTEM = "system";
    private static final String KIND_AGENT = "agent";
    private static final String COORDINATOR = "COORDINATOR";
    private static final ToolResultMetadata NO_METADATA = new ToolResultMetadata(List.of(), false);

    private final CoordinatorAgent coordinatorAgent;
    private final AgentOrchestrationService agentOrchestrationService;
    private final ThreadLocal<ToolResultAccumulator> invocationMetadata = ThreadLocal.withInitial(ToolResultAccumulator::new);

    public StockAnalysisChatTools(
            CoordinatorAgent coordinatorAgent,
            AgentOrchestrationService agentOrchestrationService
    ) {
        this.coordinatorAgent = coordinatorAgent;
        this.agentOrchestrationService = agentOrchestrationService;
    }

    @Tool(description = "Run the stock-analysis orchestration for a user's question. Use this once per user turn for in-scope stock-analysis requests, including combined requests that need fundamentals, news, technicals, and synthesis together. Pass the full request instead of decomposing it into separate tool calls unless you are only asking a clarification question.")
    public String analyzeStockRequest(
            @ToolParam(description = "The user's stock-analysis request in plain English, including any ticker or company reference resolved from conversation context.")
            String request
    ) {
        return analyzeStockRequest(request, null);
    }

    public String analyzeStockRequest(String request, String conversationId) {
        ToolResultAccumulator metadata = invocationMetadata.get();
        long coordinatorStartedAt = System.nanoTime();
        RoutingDecision routingDecision = conversationId == null
                ? coordinatorAgent.execute(request)
                : coordinatorAgent.execute(request, conversationId);
        metadata.recordInvocation(List.of(agentStep(
                COORDINATOR,
                "Coordinator",
                elapsedDurationMs(coordinatorStartedAt),
                coordinatorSummary(routingDecision)
        )));

        if (routingDecision.getFinishReason() == RoutingDecision.FinishReason.NEEDS_MORE_INPUT) {
            return routingDecision.getNextPrompt();
        }

        if (routingDecision.getFinishReason() != RoutingDecision.FinishReason.COMPLETED) {
            return resolveCoordinatorMessage(routingDecision);
        }

        AnalysisRequest analysisRequest = coordinatorAgent.toAnalysisRequest(routingDecision);
        AnalysisResponse response = agentOrchestrationService.processRequest(analysisRequest, routingDecision);
        String renderedResponse = renderAnalysis(response);
        metadata.recordInvocation(extractExecutionSteps(response));
        metadata.markCacheable(response.limitations().isEmpty());
        return renderedResponse;
    }

    public void resetInvocationMetadata() {
        invocationMetadata.remove();
    }

    public ToolResultMetadata consumeInvocationMetadata() {
        ToolResultAccumulator metadata = invocationMetadata.get();
        invocationMetadata.remove();
        return metadata == null ? NO_METADATA : metadata.snapshot();
    }

    private String resolveCoordinatorMessage(RoutingDecision routingDecision) {
        if (routingDecision.getFinalResponse() != null && !routingDecision.getFinalResponse().isBlank()) {
            return routingDecision.getFinalResponse();
        }

        if (routingDecision.getNextPrompt() != null && !routingDecision.getNextPrompt().isBlank()) {
            return routingDecision.getNextPrompt();
        }

        return "I could not complete the stock-analysis request.";
    }

    private String renderAnalysis(AnalysisResponse response) {
        if (response.limitations().isEmpty()) {
            return response.answer();
        }

        return "%s\n\nLimitations: %s"
                .formatted(response.answer(), String.join(" ", response.limitations()));
    }

    private List<ChatExecutionStep> extractExecutionSteps(AnalysisResponse response) {
        if (response.agentExecutions() == null) {
            return List.of();
        }

        return response.agentExecutions().stream()
                .filter(agentExecution -> agentExecution.status() != AgentExecutionStatus.SKIPPED)
                .map(this::toExecutionStep)
                .toList();
    }

    private long elapsedDurationMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    public record ToolResultMetadata(
            List<ChatExecutionStep> executionSteps,
            boolean cacheable
    ) {
    }

    private ChatExecutionStep toExecutionStep(AgentExecution agentExecution) {
        return agentStep(
                agentExecution.agentType().name(),
                formatAgentLabel(agentExecution.agentType()),
                agentExecution.durationMs(),
                agentExecution.summary()
        );
    }

    private ChatExecutionStep systemStep(String id, String label, long durationMs, String summary) {
        return new ChatExecutionStep(id, label, KIND_SYSTEM, durationMs, summary);
    }

    private ChatExecutionStep agentStep(String id, String label, long durationMs, String summary) {
        return new ChatExecutionStep(id, label, KIND_AGENT, durationMs, summary);
    }

    private String coordinatorSummary(RoutingDecision routingDecision) {
        return switch (routingDecision.getFinishReason()) {
            case COMPLETED -> {
                List<String> routedAgents = routingDecision.getSelectedAgents() == null
                        ? List.of()
                        : routingDecision.getSelectedAgents().stream()
                        .map(this::formatAgentLabel)
                        .toList();
                String routedAgentSummary = routedAgents.isEmpty()
                        ? "no specialized agents"
                        : String.join(", ", routedAgents);
                if (routingDecision.isRequiresSynthesis()) {
                    routedAgentSummary = routedAgentSummary + ", Synthesis";
                }

                String ticker = normalizeText(routingDecision.getResolvedTicker());
                String reasoning = normalizeText(routingDecision.getReasoning());
                String baseSummary = ticker == null
                        ? "Routed the request to %s.".formatted(routedAgentSummary)
                        : "Resolved %s and routed the request to %s.".formatted(ticker.toUpperCase(), routedAgentSummary);

                yield reasoning == null ? baseSummary : "%s Reasoning: %s".formatted(baseSummary, reasoning);
            }
            case NEEDS_MORE_INPUT -> {
                String nextPrompt = normalizeText(routingDecision.getNextPrompt());
                yield nextPrompt == null
                        ? "Requested a clarification before routing the analysis."
                        : "Requested clarification before routing: %s".formatted(nextPrompt);
            }
            case OUT_OF_SCOPE, CANNOT_PROCEED -> {
                String finalResponse = normalizeText(routingDecision.getFinalResponse());
                yield finalResponse == null
                        ? "Could not route the request to the stock-analysis agents."
                        : finalResponse;
            }
        };
    }

    private String formatAgentLabel(AgentType agentType) {
        return switch (agentType) {
            case MARKET_DATA -> "Market Data";
            case FUNDAMENTALS -> "Fundamentals";
            case NEWS -> "News";
            case TECHNICAL_ANALYSIS -> "Technical Analysis";
            case SYNTHESIS -> "Synthesis";
        };
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        return normalized.isBlank() ? null : normalized;
    }

    private static final class ToolResultAccumulator {

        private int invocationCount;
        private boolean cacheable;
        private final Map<String, ChatExecutionStep> executionSteps = new LinkedHashMap<>();

        private void recordInvocation(List<ChatExecutionStep> steps) {
            invocationCount += 1;

            for (ChatExecutionStep step : steps) {
                executionSteps.merge(step.id(), step, ToolResultAccumulator::mergeExecutionStep);
            }
        }

        private void markCacheable(boolean cacheable) {
            this.cacheable = this.cacheable || cacheable;
        }

        private ToolResultMetadata snapshot() {
            if (invocationCount == 0) {
                return NO_METADATA;
            }

            return new ToolResultMetadata(
                    List.copyOf(executionSteps.values()),
                    cacheable
            );
        }

        private static ChatExecutionStep mergeExecutionStep(ChatExecutionStep existing, ChatExecutionStep incoming) {
            return new ChatExecutionStep(
                    incoming.id(),
                    incoming.label(),
                    incoming.kind(),
                    existing.durationMs() + incoming.durationMs(),
                    mergeSummaries(existing.summary(), incoming.summary())
            );
        }

        private static String mergeSummaries(String existing, String incoming) {
            if (incoming == null || incoming.isBlank()) {
                return existing;
            }

            if (existing == null || existing.isBlank()) {
                return incoming;
            }

            if (existing.equals(incoming)) {
                return existing;
            }

            return existing + "\n\n" + incoming;
        }
    }
}
