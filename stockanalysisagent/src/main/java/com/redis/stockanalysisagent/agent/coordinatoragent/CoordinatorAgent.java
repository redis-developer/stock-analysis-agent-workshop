package com.redis.stockanalysisagent.agent.coordinatoragent;

import com.redis.stockanalysisagent.agent.orchestration.AgentType;
import com.redis.stockanalysisagent.agent.orchestration.AnalysisRequest;
import com.redis.stockanalysisagent.agent.orchestration.TokenUsageSummary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Service
public class CoordinatorAgent {

    private final CoordinatorRoutingAgent coordinatorRoutingAgent;

    public CoordinatorAgent(CoordinatorRoutingAgent coordinatorRoutingAgent) {
        this.coordinatorRoutingAgent = coordinatorRoutingAgent;
    }

    public ExecutionPlan createPlan(RoutingDecision routingDecision) {
        if (routingDecision.getFinishReason() != RoutingDecision.FinishReason.COMPLETED) {
            throw new IllegalStateException(
                    "Coordinator routing must be COMPLETED before an execution plan can be created."
            );
        }

        List<AgentType> selectedAgents = new ArrayList<>(new LinkedHashSet<>(selectedSpecialists(routingDecision.getSelectedAgents())));

        if (selectedAgents.isEmpty()) {
            throw new IllegalStateException("Coordinator routing returned no specialized agents.");
        }

        selectedAgents.add(AgentType.SYNTHESIS);

        return new ExecutionPlan(
                List.copyOf(selectedAgents),
                routingDecision.getReasoning()
        );
    }

    public RoutingOutcome execute(String userMessage, String conversationId) {
        CoordinatorRoutingAgent.RoutingResult routingResult = coordinatorRoutingAgent.route(userMessage, conversationId);
        RoutingDecision routingDecision = routingResult.routingDecision();
        if (routingDecision.getFinishReason() == RoutingDecision.FinishReason.NEEDS_MORE_INPUT) {
            routingDecision.setConversationId(conversationId);
        }
        return new RoutingOutcome(routingDecision, routingResult.tokenUsage());
    }

    public AnalysisRequest toAnalysisRequest(RoutingDecision routingDecision) {
        return new AnalysisRequest(
                routingDecision.getResolvedTicker().trim().toUpperCase(),
                routingDecision.getResolvedQuestion().trim()
        );
    }

    private List<AgentType> selectedSpecialists(List<AgentType> selectedAgents) {
        return (selectedAgents == null ? List.<AgentType>of() : selectedAgents).stream()
                .filter(Objects::nonNull)
                .filter(agentType -> agentType != AgentType.SYNTHESIS)
                .distinct()
                .toList();
    }

    public record RoutingOutcome(
            RoutingDecision routingDecision,
            TokenUsageSummary tokenUsage
    ) {
    }
}
