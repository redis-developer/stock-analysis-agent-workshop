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
        // PART 4 STEP 3A:
        // Replace this method body with the snippet from the Part 4 guide.
        throw new UnsupportedOperationException("Part 4: implement createPlan(...)");
    }

    public RoutingOutcome execute(String userMessage, String conversationId) {
        // PART 4 STEP 3B:
        // Replace this method body with the snippet from the Part 4 guide.
        throw new UnsupportedOperationException("Part 4: implement execute(...)");
    }

    public AnalysisRequest toAnalysisRequest(RoutingDecision routingDecision) {
        // PART 4 STEP 3C:
        // Replace this method body with the snippet from the Part 4 guide.
        throw new UnsupportedOperationException("Part 4: implement toAnalysisRequest(...)");
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
