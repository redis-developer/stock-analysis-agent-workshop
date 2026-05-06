package com.redis.stockanalysisagent.chat.api;

import com.redis.stockanalysisagent.agent.orchestration.TokenUsageSummary;
import com.redis.stockanalysisagent.chat.ChatExecutionStep;

import java.util.List;

public record ChatResponse(
        String userId,
        String sessionId,
        String conversationId,
        String response,
        List<String> retrievedMemories,
        boolean fromSemanticCache,
        boolean fromSemanticGuardrail,
        TokenUsageSummary tokenUsage,
        List<ChatExecutionStep> executionSteps,
        long responseTimeMs
) {
}
