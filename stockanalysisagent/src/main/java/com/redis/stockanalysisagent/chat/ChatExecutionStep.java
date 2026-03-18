package com.redis.stockanalysisagent.chat;

import com.redis.stockanalysisagent.agent.orchestration.TokenUsageSummary;

public record ChatExecutionStep(
        String id,
        String label,
        String kind,
        long durationMs,
        String summary,
        TokenUsageSummary tokenUsage
) {
}
