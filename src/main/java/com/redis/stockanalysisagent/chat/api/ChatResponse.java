package com.redis.stockanalysisagent.chat.api;

import com.redis.stockanalysisagent.chat.ChatExecutionStep;

import java.util.List;

public record ChatResponse(
        String userId,
        String sessionId,
        String conversationId,
        String response,
        List<String> retrievedMemories,
        boolean fromSemanticCache,
        List<ChatExecutionStep> triggeredAgents,
        long responseTimeMs
) {
}
