package com.redis.stockanalysisagent.chat;

import com.redis.stockanalysisagent.agent.coordinatoragent.CoordinatorAgent;
import com.redis.stockanalysisagent.agent.coordinatoragent.RoutingDecision;
import com.redis.stockanalysisagent.agent.orchestration.AgentExecution;
import com.redis.stockanalysisagent.agent.orchestration.AgentExecutionStatus;
import com.redis.stockanalysisagent.agent.orchestration.AgentOrchestrationService;
import com.redis.stockanalysisagent.agent.orchestration.AgentType;
import com.redis.stockanalysisagent.agent.orchestration.AnalysisRequest;
import com.redis.stockanalysisagent.agent.orchestration.AnalysisResponse;
import com.redis.stockanalysisagent.agent.orchestration.TokenUsageSummary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
class ChatRunner {

    private static final String KIND_AGENT = "agent";
    private static final String COORDINATOR = "COORDINATOR";

    private final CoordinatorAgent coordinatorAgent;
    private final AgentOrchestrationService agentOrchestrationService;

    ChatRunner(
            CoordinatorAgent coordinatorAgent,
            AgentOrchestrationService agentOrchestrationService
    ) {
        this.coordinatorAgent = coordinatorAgent;
        this.agentOrchestrationService = agentOrchestrationService;
    }

    AnalysisTurn analyze(String request, String conversationId) {
        long coordinatorStartedAt = System.nanoTime();
        CoordinatorAgent.RoutingOutcome routingOutcome = coordinatorAgent.execute(request, conversationId);
        RoutingDecision routingDecision = routingOutcome.routingDecision();
        List<ChatExecutionStep> executionSteps = new ArrayList<>();
        executionSteps.add(agentStep(
                COORDINATOR,
                "Coordinator",
                elapsedDurationMs(coordinatorStartedAt),
                coordinatorSummary(routingDecision),
                routingOutcome.tokenUsage()
        ));

        if (routingDecision.getFinishReason() == RoutingDecision.FinishReason.NEEDS_MORE_INPUT) {
            return new AnalysisTurn(
                    routingDecision.getNextPrompt(),
                    List.copyOf(executionSteps),
                    false,
                    TokenUsageSummary.sum(executionSteps.stream().map(ChatExecutionStep::tokenUsage).toList())
            );
        }

        if (routingDecision.getFinishReason() != RoutingDecision.FinishReason.COMPLETED) {
            return new AnalysisTurn(
                    resolveCoordinatorMessage(routingDecision),
                    List.copyOf(executionSteps),
                    false,
                    TokenUsageSummary.sum(executionSteps.stream().map(ChatExecutionStep::tokenUsage).toList())
            );
        }

        AnalysisRequest analysisRequest = coordinatorAgent.toAnalysisRequest(routingDecision);
        AnalysisResponse response = agentOrchestrationService.processRequest(analysisRequest, routingDecision);
        executionSteps.addAll(extractExecutionSteps(response));

        return new AnalysisTurn(
                renderAnalysis(response),
                List.copyOf(executionSteps),
                response.limitations().isEmpty(),
                TokenUsageSummary.sum(executionSteps.stream().map(ChatExecutionStep::tokenUsage).toList())
        );
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

    private ChatExecutionStep toExecutionStep(AgentExecution agentExecution) {
        return agentStep(
                agentExecution.agentType().name(),
                formatAgentLabel(agentExecution.agentType()),
                agentExecution.durationMs(),
                agentExecution.summary(),
                agentExecution.tokenUsage()
        );
    }

    private ChatExecutionStep agentStep(
            String id,
            String label,
            long durationMs,
            String summary,
            TokenUsageSummary tokenUsage
    ) {
        return new ChatExecutionStep(id, label, KIND_AGENT, durationMs, summary, tokenUsage);
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
                        ? "Synthesis"
                        : String.join(", ", routedAgents) + ", Synthesis";

                String ticker = routingDecision.getResolvedTicker();
                String reasoning = routingDecision.getReasoning();
                String baseSummary = ticker == null
                        ? "Routed the request to %s.".formatted(routedAgentSummary)
                        : "Resolved %s and routed the request to %s.".formatted(ticker.toUpperCase(), routedAgentSummary);

                yield reasoning == null ? baseSummary : "%s Reasoning: %s".formatted(baseSummary, reasoning);
            }
            case NEEDS_MORE_INPUT -> routingDecision.getNextPrompt();
            case OUT_OF_SCOPE, CANNOT_PROCEED -> routingDecision.getFinalResponse();
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

    private long elapsedDurationMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    record AnalysisTurn(
            String response,
            List<ChatExecutionStep> executionSteps,
            boolean cacheable,
            TokenUsageSummary tokenUsage
    ) {
    }
}
