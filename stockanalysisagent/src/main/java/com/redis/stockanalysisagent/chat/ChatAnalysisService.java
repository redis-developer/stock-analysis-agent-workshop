package com.redis.stockanalysisagent.chat;

import com.redis.stockanalysisagent.agent.coordinatoragent.CoordinatorAgent;
import com.redis.stockanalysisagent.agent.coordinatoragent.ExecutionPlan;
import com.redis.stockanalysisagent.agent.coordinatoragent.RoutingDecision;
import com.redis.stockanalysisagent.agent.orchestration.AgentExecution;
import com.redis.stockanalysisagent.agent.orchestration.AgentOrchestrationService;
import com.redis.stockanalysisagent.agent.orchestration.AnalysisRequest;
import com.redis.stockanalysisagent.agent.orchestration.AnalysisResponse;
import com.redis.stockanalysisagent.agent.orchestration.TokenUsageSummary;
import com.redis.stockanalysisagent.semanticcache.SemanticAnalysisCache;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.redis.stockanalysisagent.observability.OrchestrationObservability.*;

@Service
class ChatAnalysisService {

    private static final String KIND_AGENT = "agent";
    private static final String KIND_SYSTEM = "system";
    private static final String COORDINATOR = "COORDINATOR";
    private static final String SEMANTIC_CACHE = "SEMANTIC_CACHE";

    private final CoordinatorAgent coordinatorAgent;
    private final AgentOrchestrationService agentOrchestrationService;
    private final ObservationRegistry observationRegistry;
    private final SemanticAnalysisCache semanticAnalysisCache;

    ChatAnalysisService(
            CoordinatorAgent coordinatorAgent,
            AgentOrchestrationService agentOrchestrationService,
            ObservationRegistry observationRegistry,
            SemanticAnalysisCache semanticAnalysisCache
    ) {
        this.coordinatorAgent = coordinatorAgent;
        this.agentOrchestrationService = agentOrchestrationService;
        this.observationRegistry = observationRegistry;
        this.semanticAnalysisCache = semanticAnalysisCache;
    }

    AnalysisTurn analyze(String request, String conversationId, Integer retrievedMemoriesLimit) {
        long coordinatorStartedAt = System.nanoTime();
        CoordinatorAgent.RoutingOutcome routingOutcome = observeCoordinator(
                request,
                conversationId,
                retrievedMemoriesLimit
        );
        RoutingDecision routingDecision = routingOutcome.routingDecision();
        List<ChatExecutionStep> executionSteps = new ArrayList<>();
        executionSteps.add(cacheStep(routingOutcome.cacheHit()));
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
                    routingOutcome.cacheHit(),
                    TokenUsageSummary.sum(executionSteps.stream().map(ChatExecutionStep::tokenUsage).toList())
            );
        }

        if (routingDecision.getFinishReason() == RoutingDecision.FinishReason.DIRECT_RESPONSE) {
            return new AnalysisTurn(
                    resolveCoordinatorMessage(routingDecision),
                    List.copyOf(executionSteps),
                    routingOutcome.cacheHit(),
                    TokenUsageSummary.sum(executionSteps.stream().map(ChatExecutionStep::tokenUsage).toList())
            );
        }

        if (routingDecision.getFinishReason() != RoutingDecision.FinishReason.COMPLETED) {
            return new AnalysisTurn(
                    resolveCoordinatorMessage(routingDecision),
                    List.copyOf(executionSteps),
                    routingOutcome.cacheHit(),
                    TokenUsageSummary.sum(executionSteps.stream().map(ChatExecutionStep::tokenUsage).toList())
            );
        }

        AnalysisRequest analysisRequest = coordinatorAgent.toAnalysisRequest(routingDecision);
        ExecutionPlan executionPlan = coordinatorAgent.createPlan(routingDecision);
        AnalysisResponse response = agentOrchestrationService.processRequest(analysisRequest, executionPlan);
        executionSteps.addAll(extractExecutionSteps(response));
        semanticAnalysisCache.storeResponse(request, response.answer());

        return new AnalysisTurn(
                response.answer(),
                List.copyOf(executionSteps),
                false,
                TokenUsageSummary.sum(executionSteps.stream().map(ChatExecutionStep::tokenUsage).toList())
        );
    }

    private CoordinatorAgent.RoutingOutcome observeCoordinator(
            String request,
            String conversationId,
            Integer retrievedMemoriesLimit
    ) {
        Observation observation = coordinatorObservation(observationRegistry)
                .highCardinalityKeyValue(KEY_CONVERSATION_ID, conversationId)
                .start();
        try {
            CoordinatorAgent.RoutingOutcome outcome = coordinatorAgent.execute(
                    request,
                    conversationId,
                    retrievedMemoriesLimit
            );
            enrichWithRoutingDecision(observation, outcome.routingDecision());
            enrichWithTokenUsage(observation, outcome.tokenUsage());
            if (outcome.cacheHit()) {
                observation.lowCardinalityKeyValue(KEY_SEMANTIC_CACHE_HIT, "true");
            }
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

    private ChatExecutionStep cacheStep(boolean cacheHit) {
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
            case DIRECT_RESPONSE -> routingDecision.getFinalResponse();
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
            boolean fromSemanticCache,
            TokenUsageSummary tokenUsage
    ) {
    }
}
