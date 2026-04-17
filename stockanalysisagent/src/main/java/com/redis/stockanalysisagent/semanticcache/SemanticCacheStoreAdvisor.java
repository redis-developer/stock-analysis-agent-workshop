package com.redis.stockanalysisagent.semanticcache;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Semantic cache store advisor for the final ChatClient call.
 */
public class SemanticCacheStoreAdvisor implements CallAdvisor {

    public static final String CACHE_STORED = SemanticCacheSupport.CACHE_STORED;
    private static final Logger log = LoggerFactory.getLogger(SemanticCacheStoreAdvisor.class);
    private static final int DEFAULT_ORDER = 200;

    private final SemanticAnalysisCache semanticCache;

    public SemanticCacheStoreAdvisor(SemanticAnalysisCache semanticCache) {
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
        ChatClientResponse response = chain.nextCall(request);
        String cacheKey = SemanticCacheSupport.resolveCacheKey(request);
        if (cacheKey == null) {
            return response;
        }

        String finalResponse = SemanticCacheSupport.extractFinalResponse(response);
        if (finalResponse == null || finalResponse.isBlank()) {
            return response;
        }

        try {
            semanticCache.storeFinalResponse(cacheKey, finalResponse);
            return response.mutate()
                    .chatResponse(withStoredMetadata(response.chatResponse()))
                    .context(SemanticCacheSupport.CACHE_KEY, cacheKey)
                    .context(CACHE_STORED, true)
                    .build();
        } catch (RuntimeException ex) {
            log.warn("Skipping semantic cache store because persistence failed.", ex);
            return response;
        }
    }

    private ChatResponse withStoredMetadata(ChatResponse chatResponse) {
        if (chatResponse == null) {
            return null;
        }

        ChatResponseMetadata existingMetadata = chatResponse.getMetadata();
        ChatResponseMetadata.Builder metadataBuilder = ChatResponseMetadata.builder();
        if (existingMetadata != null) {
            Map<String, Object> metadataValues = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : existingMetadata.entrySet()) {
                metadataValues.put(entry.getKey(), entry.getValue());
            }
            metadataBuilder
                    .metadata(metadataValues)
                    .id(existingMetadata.getId())
                    .model(existingMetadata.getModel())
                    .rateLimit(existingMetadata.getRateLimit())
                    .usage(existingMetadata.getUsage())
                    .promptMetadata(existingMetadata.getPromptMetadata());
        }

        metadataBuilder.keyValue(CACHE_STORED, true);
        return new ChatResponse(chatResponse.getResults(), metadataBuilder.build());
    }
}
