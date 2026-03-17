package com.redis.stockanalysisagent.chat.api;

import java.util.List;

public record ChatResponse(
        String userId,
        String sessionId,
        String conversationId,
        String response,
        List<String> retrievedMemories,
        boolean fromSemanticCache,
        List<String> triggeredAgents,
        long responseTimeMs
) {
}
