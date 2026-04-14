package com.redis.stockanalysisagent.agent.coordinatoragent;

import com.redis.stockanalysisagent.agent.orchestration.TokenUsageSummary;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class CoordinatorRoutingAgent {
    private final ChatClient coordinatorChatClient;

    public CoordinatorRoutingAgent(
            @Qualifier("coordinatorChatClient") ChatClient coordinatorChatClient
    ) {
        this.coordinatorChatClient = coordinatorChatClient;
    }

    public RoutingResult route(String userMessage, String conversationId) {
        // PART 4 STEP 2:
        // Replace this method body with the snippet from the Part 4 guide.
        // PART 8 STEP 9:
        // After wiring advisor-based semantic caching, update this method so it:
        // 1. passes the conversation id into the advisor context
        // 2. reads the CACHE_HIT marker from ChatResponse metadata
        // 3. returns that flag in RoutingResult
        throw new UnsupportedOperationException("Part 4 and Part 8: implement route(...)");
    }

    public record RoutingResult(
            RoutingDecision routingDecision,
            TokenUsageSummary tokenUsage,
            boolean fromSemanticCache
    ) {
    }
}
