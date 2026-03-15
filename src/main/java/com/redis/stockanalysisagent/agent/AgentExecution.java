package com.redis.stockanalysisagent.agent;

public record AgentExecution(
        AgentType agentType,
        AgentExecutionStatus status,
        String summary
) {
}
