package com.redis.stockanalysisagent.agent.coordinatoragent;

import com.redis.stockanalysisagent.agent.orchestration.TokenUsageSummary;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
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
        throw new UnsupportedOperationException("Part 4: implement route(...)");
    }

    public record RoutingResult(
            RoutingDecision routingDecision,
            TokenUsageSummary tokenUsage
    ) {
    }
}
