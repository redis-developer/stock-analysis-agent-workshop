package com.redis.stockanalysisagent.chat.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        String userId,
        String sessionId,
        @NotBlank String message,
        @Min(1)
        @Max(20)
        Integer retrievedMemoriesLimit
) {
}
