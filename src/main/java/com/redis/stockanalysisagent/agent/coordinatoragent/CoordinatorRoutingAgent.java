package com.redis.stockanalysisagent.agent.coordinatoragent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class CoordinatorRoutingAgent {
    private final ChatClient coordinatorChatClient;

    public CoordinatorRoutingAgent(@Qualifier("coordinatorChatClient") ChatClient coordinatorChatClient) {
        this.coordinatorChatClient = coordinatorChatClient;
    }

    public RoutingDecision route(String userMessage, String conversationId) {
        ResponseEntity<ChatResponse, RoutingDecision> response = coordinatorChatClient
                .prompt()
                .user(userMessage)
                .advisors(spec -> spec.param(org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .responseEntity(RoutingDecision.class);

        RoutingDecision decision = response.entity();
        if (decision == null) {
            throw new IllegalStateException("Coordinator routing returned an empty response.");
        }

        return decision;
    }
}
