package com.redis.stockanalysisagent.chat;

public record ChatExecutionStep(
        String agentType,
        long durationMs
) {
}
