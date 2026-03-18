package com.redis.stockanalysisagent.agent.orchestration;

public record AgentExecution(
        AgentType agentType,
        AgentExecutionStatus status,
        String summary,
        long durationMs,
        TokenUsageSummary tokenUsage
) {
}
