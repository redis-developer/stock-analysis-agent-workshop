package com.redis.stockanalysisagent.semanticcache;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

/**
 * Semantic cache lookup advisor.
 */
public class SemanticCacheAdvisor implements CallAdvisor {

    public static final String CACHE_HIT = SemanticCacheSupport.CACHE_HIT;
    public static final String BYPASS_CACHE = SemanticCacheSupport.CACHE_BYPASS;
    public static final String CACHE_KEY = SemanticCacheSupport.CACHE_KEY;
    private static final int DEFAULT_ORDER = 20;

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
        if (SemanticCacheSupport.shouldBypass(request)) {
            return chain.nextCall(request);
        }

        String cacheKey = SemanticCacheSupport.resolveCacheKey(request);
        if (cacheKey == null) {
            return chain.nextCall(request);
        }

        var cachedResponse = semanticCache.findCachedResponse(cacheKey);
        if (cachedResponse.isPresent()) {
            return SemanticCacheSupport.asCacheHitResponse(cacheKey, cachedResponse.get(), request.context());
        }

        ChatClientResponse response = chain.nextCall(request);
        return response == null ? null : SemanticCacheSupport.markMiss(response, cacheKey);
    }
}
