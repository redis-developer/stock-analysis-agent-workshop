package com.redis.stockanalysisagent.chat.api;

import com.redis.stockanalysisagent.agent.AgentExecution;

import java.util.List;

public record ChatResponse(
        String userId,
        String sessionId,
        String conversationId,
        String response,
        List<String> retrievedMemories,
        boolean fromSemanticCache,
        List<AgentExecution> triggeredAgents,
        long responseTimeMs
) {
}
