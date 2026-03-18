package com.redis.stockanalysisagent.agent.synthesisagent;

import com.redis.stockanalysisagent.agent.orchestration.TokenUsageSummary;

public record SynthesisResult(
        String finalAnswer,
        TokenUsageSummary tokenUsage
) {
}
