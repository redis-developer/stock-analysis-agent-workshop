package com.redis.stockanalysisagent.agent.coordinatoragent;

import com.redis.stockanalysisagent.agent.orchestration.AgentType;

import java.util.List;

public record ExecutionPlan(
        List<AgentType> selectedAgents,
        boolean requiresSynthesis,
        String routingReasoning
) {
}
