package com.redis.stockanalysisagent.chat;

import com.redis.stockanalysisagent.agent.coordinatoragent.CoordinatorAgent;
import com.redis.stockanalysisagent.agent.coordinatoragent.ExecutionPlan;
import com.redis.stockanalysisagent.agent.coordinatoragent.RoutingDecision;
import com.redis.stockanalysisagent.agent.orchestration.AgentExecution;
import com.redis.stockanalysisagent.agent.orchestration.AgentExecutionStatus;
import com.redis.stockanalysisagent.agent.orchestration.AgentOrchestrationService;
import com.redis.stockanalysisagent.agent.orchestration.AnalysisRequest;
import com.redis.stockanalysisagent.agent.orchestration.AnalysisResponse;
import com.redis.stockanalysisagent.agent.orchestration.TokenUsageSummary;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.redis.stockanalysisagent.observability.OrchestrationObservability.*;

@Service
class ChatAnalysisService {

    private static final String KIND_AGENT = "agent";
    private static final String COORDINATOR = "COORDINATOR";

    private final CoordinatorAgent coordinatorAgent;
    private final AgentOrchestrationService agentOrchestrationService;
    private final ObservationRegistry observationRegistry;

    ChatAnalysisService(
            CoordinatorAgent coordinatorAgent,
            AgentOrchestrationService agentOrchestrationService,
            ObservationRegistry observationRegistry
    ) {
        this.coordinatorAgent = coordinatorAgent;
        this.agentOrchestrationService = agentOrchestrationService;
        this.observationRegistry = observationRegistry;
    }

    AnalysisTurn analyze(String request, String conversationId) {
        long coordinatorStartedAt = System.nanoTime();
        CoordinatorAgent.RoutingOutcome routingOutcome = observeCoordinator(request, conversationId);
        RoutingDecision routingDecision = routingOutcome.routingDecision();
        List<ChatExecutionStep> executionSteps = new ArrayList<>();
        executionSteps.add(analysisStep(
                COORDINATOR,
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
        ExecutionPlan executionPlan = coordinatorAgent.createPlan(routingDecision);
        AnalysisResponse response = agentOrchestrationService.processRequest(analysisRequest, executionPlan);
        executionSteps.addAll(extractExecutionSteps(response));

        return new AnalysisTurn(
                response.answer(),
                List.copyOf(executionSteps),
                true,
                TokenUsageSummary.sum(executionSteps.stream().map(ChatExecutionStep::tokenUsage).toList())
        );
    }

    private CoordinatorAgent.RoutingOutcome observeCoordinator(String request, String conversationId) {
        Observation observation = coordinatorObservation(observationRegistry)
                .highCardinalityKeyValue(KEY_CONVERSATION_ID, conversationId)
                .start();
        try {
            CoordinatorAgent.RoutingOutcome outcome = coordinatorAgent.execute(request, conversationId);
            enrichWithRoutingDecision(observation, outcome.routingDecision());
            enrichWithTokenUsage(observation, outcome.tokenUsage());
            return outcome;
        } catch (Throwable t) {
            observation.error(t);
            throw t;
        } finally {
            observation.stop();
        }
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
        return analysisStep(
                agentExecution.agentType().name(),
                agentExecution.durationMs(),
                agentExecution.summary(),
                agentExecution.tokenUsage()
        );
    }

    private ChatExecutionStep analysisStep(
            String id,
            long durationMs,
            String summary,
            TokenUsageSummary tokenUsage
    ) {
        return new ChatExecutionStep(id, null, KIND_AGENT, durationMs, summary, tokenUsage);
    }

    private String coordinatorSummary(RoutingDecision routingDecision) {
        return switch (routingDecision.getFinishReason()) {
            case COMPLETED -> {
                String ticker = routingDecision.getResolvedTicker();
                String reasoning = routingDecision.getReasoning();
                String baseSummary = ticker == null
                        ? "Built the execution plan."
                        : "Resolved %s and built the execution plan.".formatted(ticker.toUpperCase());

                yield reasoning == null ? baseSummary : "%s Reasoning: %s".formatted(baseSummary, reasoning);
            }
            case NEEDS_MORE_INPUT -> routingDecision.getNextPrompt();
            case OUT_OF_SCOPE, CANNOT_PROCEED -> routingDecision.getFinalResponse();
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
