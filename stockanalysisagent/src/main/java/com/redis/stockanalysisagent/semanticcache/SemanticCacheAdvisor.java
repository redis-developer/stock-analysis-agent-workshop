package com.redis.stockanalysisagent.semanticcache;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import tools.jackson.core.io.JsonStringEncoder;

import java.util.List;

/**
 * Semantic cache advisor for coordinator ChatClient calls.
 */
public class SemanticCacheAdvisor implements CallAdvisor {

    public static final String CACHE_HIT = "semantic_cache_hit";
    public static final String BYPASS_CACHE = "semantic_cache_bypass";
    private static final int DEFAULT_ORDER = 50;
    private static final JsonStringEncoder JSON_STRING_ENCODER = JsonStringEncoder.getInstance();

    private final SemanticAnalysisCache semanticCache;

    public SemanticCacheAdvisor(SemanticAnalysisCache semanticCache) {
        this.semanticCache = semanticCache;
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return DEFAULT_ORDER;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        if (Boolean.TRUE.equals(request.context().get(BYPASS_CACHE))) {
            return chain.nextCall(request);
        }

        String cacheKey = cacheKey(request);
        if (cacheKey == null) {
            return chain.nextCall(request);
        }

        var cachedResponse = semanticCache.findResponse(cacheKey);
        if (cachedResponse.isPresent()) {
            return ChatClientResponse.builder()
                    .chatResponse(toChatResponse(cachedResponse.get()))
                    .context(request.context())
                    .context(CACHE_HIT, true)
                    .build();
        }

        return chain.nextCall(request).mutate()
                .context(CACHE_HIT, false)
                .build();
    }

    private String cacheKey(ChatClientRequest request) {
        if (request == null || request.prompt() == null || request.prompt().getUserMessage() == null) {
            return null;
        }

        String text = request.prompt().getUserMessage().getText();
        if (text == null) {
            return null;
        }

        String normalized = text.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private ChatResponse toChatResponse(String finalResponse) {
        return ChatResponse.builder()
                .metadata(ChatResponseMetadata.builder()
                        .keyValue(CACHE_HIT, true)
                        .build())
                .generations(List.of(new Generation(new AssistantMessage(toCoordinatorPayload(finalResponse)))))
                .build();
    }

    private String toCoordinatorPayload(String finalResponse) {
        StringBuilder escapedFinalResponse = new StringBuilder();
        JSON_STRING_ENCODER.quoteAsString(finalResponse == null ? "" : finalResponse, escapedFinalResponse);
        return "{\"finishReason\":\"DIRECT_RESPONSE\",\"finalResponse\":\"%s\"}".formatted(escapedFinalResponse);
    }
}
