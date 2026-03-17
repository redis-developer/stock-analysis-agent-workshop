package com.redis.stockanalysisagent.agent.coordinatoragent;

import com.redis.stockanalysisagent.agent.AgentType;
import com.redis.stockanalysisagent.api.AnalysisRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class CoordinatorAgent {

    private final CoordinatorRoutingAgent coordinatorRoutingAgent;

    public CoordinatorAgent(CoordinatorRoutingAgent coordinatorRoutingAgent) {
        this.coordinatorRoutingAgent = coordinatorRoutingAgent;
    }

    public ExecutionPlan createPlan(AnalysisRequest request) {
        return createPlan(execute(request));
    }

    public ExecutionPlan createPlan(RoutingDecision routingDecision) {
        RoutingDecision normalizedDecision = normalizeRoutingDecision(routingDecision);

        if (normalizedDecision.getFinishReason() != RoutingDecision.FinishReason.COMPLETED) {
            throw new IllegalStateException(
                    "Coordinator routing must be COMPLETED before an execution plan can be created."
            );
        }

        List<AgentType> selectedAgents = new ArrayList<>(new LinkedHashSet<>(normalizedDecision.getSelectedAgents()));
        selectedAgents.remove(AgentType.SYNTHESIS);

        if (selectedAgents.isEmpty()) {
            throw new IllegalStateException("Coordinator routing returned no specialized agents.");
        }

        boolean requiresSynthesis = normalizedDecision.isRequiresSynthesis();
        if (requiresSynthesis) {
            selectedAgents.add(AgentType.SYNTHESIS);
        }

        return new ExecutionPlan(
                List.copyOf(selectedAgents),
                requiresSynthesis,
                normalizedDecision.getReasoning()
        );
    }

    public RoutingDecision execute(AnalysisRequest request) {
        return normalizeRoutingDecision(
                coordinatorRoutingAgent.route(request),
                request.ticker(),
                request.question()
        );
    }

    public RoutingDecision execute(String userMessage) {
        return execute(userMessage, UUID.randomUUID().toString());
    }

    public RoutingDecision execute(String userMessage, String conversationId) {
        RoutingDecision routingDecision = normalizeRoutingDecision(
                coordinatorRoutingAgent.route(userMessage, conversationId),
                null,
                null
        );
        if (routingDecision.getFinishReason() == RoutingDecision.FinishReason.NEEDS_MORE_INPUT) {
            routingDecision.setConversationId(conversationId);
        }
        return routingDecision;
    }

    public AnalysisRequest toAnalysisRequest(RoutingDecision routingDecision) {
        if (routingDecision.getFinishReason() != RoutingDecision.FinishReason.COMPLETED) {
            throw new IllegalStateException(
                    "Coordinator routing must be COMPLETED before it can be converted into an analysis request."
            );
        }

        String resolvedTicker = normalizeRequiredValue(routingDecision.getResolvedTicker(), "resolvedTicker");
        String resolvedQuestion = normalizeRequiredValue(routingDecision.getResolvedQuestion(), "resolvedQuestion");

        return new AnalysisRequest(resolvedTicker.toUpperCase(), resolvedQuestion);
    }

    private String normalizeRequiredValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Coordinator routing returned a blank " + fieldName + ".");
        }

        return value.trim();
    }

    private RoutingDecision normalizeRoutingDecision(RoutingDecision routingDecision) {
        return normalizeRoutingDecision(routingDecision, null, null);
    }

    private RoutingDecision normalizeRoutingDecision(
            RoutingDecision routingDecision,
            String fallbackTicker,
            String fallbackQuestion
    ) {
        if (routingDecision == null) {
            return invalidCompletedDecision();
        }

        if (routingDecision.getFinishReason() != RoutingDecision.FinishReason.COMPLETED) {
            return routingDecision;
        }

        String resolvedTicker = normalizeOptionalValue(routingDecision.getResolvedTicker());
        String resolvedQuestion = normalizeOptionalValue(routingDecision.getResolvedQuestion());
        if (resolvedTicker == null) {
            resolvedTicker = normalizeOptionalValue(fallbackTicker);
        }
        if (resolvedQuestion == null) {
            resolvedQuestion = normalizeOptionalValue(fallbackQuestion);
        }

        routingDecision.setResolvedTicker(resolvedTicker);
        routingDecision.setResolvedQuestion(resolvedQuestion);
        routingDecision.setSelectedAgents(normalizeSelectedAgents(routingDecision.getSelectedAgents()));

        if (routingDecision.getResolvedTicker() == null
                || routingDecision.getResolvedQuestion() == null
                || routingDecision.getSelectedAgents().isEmpty()) {
            RoutingDecision fallback = invalidCompletedDecision();
            fallback.setReasoning(routingDecision.getReasoning());
            return fallback;
        }

        return routingDecision;
    }

    private List<AgentType> normalizeSelectedAgents(List<AgentType> selectedAgents) {
        if (selectedAgents == null) {
            return List.of();
        }

        return selectedAgents.stream()
                .filter(Objects::nonNull)
                .filter(agentType -> agentType != AgentType.SYNTHESIS)
                .distinct()
                .toList();
    }

    private String normalizeOptionalValue(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private RoutingDecision invalidCompletedDecision() {
        return RoutingDecision.cannotProceed(
                "I couldn't determine a valid stock-analysis plan for that request. " +
                        "Please mention the company or ticker and the kind of analysis you want."
        );
    }
}
