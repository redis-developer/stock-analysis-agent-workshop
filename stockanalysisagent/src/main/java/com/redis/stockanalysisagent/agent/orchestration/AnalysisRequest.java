package com.redis.stockanalysisagent.agent.orchestration;

import jakarta.validation.constraints.NotBlank;

public record AnalysisRequest(
        @NotBlank String ticker,
        @NotBlank String question
) {
}
