package com.redis.stockanalysisagent.chat.api;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        String userId,
        String sessionId,
        @NotBlank String message
) {
}
