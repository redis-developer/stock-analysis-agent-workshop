package com.redis.stockanalysisagent.agent.orchestration;

import java.util.List;

public record AnalysisResponse(
        List<AgentExecution> agentExecutions,
        String answer
) {

    public static AnalysisResponse completed(
            List<AgentExecution> agentExecutions,
            String answer
    ) {
        return new AnalysisResponse(List.copyOf(agentExecutions), answer);
    }
}
