package com.redis.stockanalysisagent.agent.orchestration;

public record AgentExecution(
        AgentType agentType,
        String summary,
        long durationMs,
        TokenUsageSummary tokenUsage
) {
}
