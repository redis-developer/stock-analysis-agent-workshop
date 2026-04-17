package com.redis.stockanalysisagent.semanticcache;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import tools.jackson.core.io.JsonStringEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

final class SemanticCacheSupport {

    static final String CACHE_HIT = "semantic_cache_hit";
    static final String CACHE_BYPASS = "semantic_cache_bypass";
    static final String CACHE_KEY = "semantic_cache_key";
    static final String CACHE_STORED = "semantic_cache_stored";

    private static final JsonStringEncoder JSON_STRING_ENCODER = JsonStringEncoder.getInstance();
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private SemanticCacheSupport() {
    }

    static String resolveCacheKey(ChatClientRequest request) {
        if (request == null) {
            return null;
        }

        Object contextCacheKey = request.context().get(CACHE_KEY);
        if (contextCacheKey instanceof String cacheKey && !cacheKey.isBlank()) {
            return cacheKey.trim();
        }

        if (request.prompt() == null || request.prompt().getUserMessage() == null) {
            return null;
        }

        String text = request.prompt().getUserMessage().getText();
        if (text == null) {
            return null;
        }

        String normalized = text.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    static boolean shouldBypass(ChatClientRequest request) {
        return request != null && Boolean.TRUE.equals(request.context().get(CACHE_BYPASS));
    }

    static ChatClientResponse asCacheHitResponse(String cacheKey, String finalResponse, Map<String, ?> context) {
        return ChatClientResponse.builder()
                .chatResponse(toCoordinatorChatResponse(finalResponse))
                .context(context)
                .context(CACHE_KEY, cacheKey)
                .context(CACHE_HIT, true)
                .build();
    }

    static ChatClientResponse markMiss(ChatClientResponse response, String cacheKey) {
        if (response == null) {
            return null;
        }

        return response.mutate()
                .context(CACHE_HIT, false)
                .context(CACHE_KEY, cacheKey)
                .build();
    }

    static String extractFinalResponse(ChatClientResponse response) {
        if (response == null || response.chatResponse() == null || response.chatResponse().getResult() == null) {
            return null;
        }

        String content = response.chatResponse().getResult().getOutput().getText();
        if (content == null) {
            return null;
        }

        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        if (!trimmed.startsWith("{")) {
            return trimmed;
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(trimmed);
            JsonNode finalAnswer = root.get("finalAnswer");
            if (finalAnswer != null && finalAnswer.isTextual()) {
                String value = finalAnswer.asText().trim();
                return value.isEmpty() ? null : value;
            }
        } catch (Exception ignored) {
        }

        return trimmed;
    }

    static String toCoordinatorPayload(String finalResponse) {
        StringBuilder escapedFinalResponse = new StringBuilder();
        JSON_STRING_ENCODER.quoteAsString(finalResponse == null ? "" : finalResponse, escapedFinalResponse);
        return "{\"finishReason\":\"DIRECT_RESPONSE\",\"finalResponse\":\"%s\"}".formatted(escapedFinalResponse);
    }

    private static ChatResponse toCoordinatorChatResponse(String finalResponse) {
        return ChatResponse.builder()
                .metadata(ChatResponseMetadata.builder()
                        .keyValue(CACHE_HIT, true)
                        .build())
                .generations(List.of(new Generation(new AssistantMessage(toCoordinatorPayload(finalResponse)))))
                .build();
    }
}
