package com.redis.stockanalysisagent.agent.coordinatoragent;

import com.redis.stockanalysisagent.agent.AgentType;
import com.redis.stockanalysisagent.api.AnalysisRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
        if (routingDecision.getFinishReason() != RoutingDecision.FinishReason.COMPLETED) {
            throw new IllegalStateException(
                    "Coordinator routing must be COMPLETED before an execution plan can be created."
            );
        }

        List<AgentType> selectedAgents = new ArrayList<>(new LinkedHashSet<>(routingDecision.getSelectedAgents()));
        selectedAgents.remove(AgentType.SYNTHESIS);

        if (selectedAgents.isEmpty()) {
            throw new IllegalStateException("Coordinator routing returned no specialized agents.");
        }

        boolean requiresSynthesis = routingDecision.isRequiresSynthesis();
        if (requiresSynthesis) {
            selectedAgents.add(AgentType.SYNTHESIS);
        }

        return new ExecutionPlan(
                List.copyOf(selectedAgents),
                requiresSynthesis,
                routingDecision.getReasoning()
        );
    }

    public RoutingDecision execute(AnalysisRequest request) {
        return coordinatorRoutingAgent.route(request);
    }

    public RoutingDecision execute(String userMessage) {
        return execute(userMessage, UUID.randomUUID().toString());
    }

    public RoutingDecision execute(String userMessage, String conversationId) {
        RoutingDecision routingDecision = coordinatorRoutingAgent.route(userMessage, conversationId);
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
}
