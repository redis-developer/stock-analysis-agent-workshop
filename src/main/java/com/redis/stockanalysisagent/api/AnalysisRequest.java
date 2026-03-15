package com.redis.stockanalysisagent.api;

import jakarta.validation.constraints.NotBlank;

public record AnalysisRequest(
        @NotBlank String ticker,
        @NotBlank String question
) {
}
