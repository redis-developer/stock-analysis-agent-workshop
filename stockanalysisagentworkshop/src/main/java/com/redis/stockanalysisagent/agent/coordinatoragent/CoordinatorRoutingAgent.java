package com.redis.stockanalysisagent.agent.coordinatoragent;

import com.redis.stockanalysisagent.agent.orchestration.TokenUsageSummary;
import com.redis.stockanalysisagent.memory.LongTermMemoryAdvisor;
import com.redis.stockanalysisagent.semanticcache.SemanticCacheAdvisor;
import com.redis.stockanalysisagent.semanticguardrail.SemanticGuardrailAdvisor;
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

    public RoutingResult route(
            String userMessage,
            String conversationId,
            Integer retrievedMemoriesLimit,
            String semanticCacheKey
    ) {
        // PART 4 STEP 2:
        // Replace this method body with the snippet from the Part 4 guide.
        // PART 8 STEP 9:
        // After wiring advisor-based semantic caching, update this method so it:
        // 1. passes conversation id, memory limit, and semantic cache key into advisor context
        // 2. reads cache and guardrail markers from ChatResponse metadata
        // 3. returns those flags in RoutingResult
        ResponseEntity<ChatResponse, RoutingDecision> response = coordinatorChatClient
                .prompt()
                .user(userMessage)
                .advisors(spec -> {
                    spec.param(org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID, conversationId);
                    spec.param(LongTermMemoryAdvisor.MAX_RETRIEVED_MEMORIES,
                            retrievedMemoriesLimit != null ? retrievedMemoriesLimit : LongTermMemoryAdvisor.DEFAULT_MAX_MEMORIES);
                    spec.param(SemanticCacheAdvisor.CACHE_KEY, semanticCacheKey);
                })
                .call()
                .responseEntity(RoutingDecision.class);

        RoutingDecision decision = response.entity();
        if (decision == null) {
            throw new IllegalStateException("Coordinator returned no routing decision.");
        }

        ChatResponse chatResponse = response.response();
        boolean cacheHit = chatResponse != null
                && Boolean.TRUE.equals(chatResponse.getMetadata().getOrDefault(SemanticCacheAdvisor.CACHE_HIT, false));
        boolean guardrailHit = chatResponse != null
                && Boolean.TRUE.equals(chatResponse.getMetadata().getOrDefault(SemanticGuardrailAdvisor.GUARDRAIL_BLOCKED, false));
        String guardrailRoute = chatResponse == null ? null : metadataString(chatResponse, SemanticGuardrailAdvisor.GUARDRAIL_ROUTE);
        return new RoutingResult(decision, TokenUsageSummary.from(chatResponse), cacheHit, guardrailHit, guardrailRoute);
    }

    private String metadataString(ChatResponse chatResponse, String key) {
        Object value = chatResponse.getMetadata().get(key);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    public record RoutingResult(
            RoutingDecision routingDecision,
            TokenUsageSummary tokenUsage,
            boolean cacheHit,
            boolean guardrailHit,
            String guardrailRoute
    ) {
    }
}
