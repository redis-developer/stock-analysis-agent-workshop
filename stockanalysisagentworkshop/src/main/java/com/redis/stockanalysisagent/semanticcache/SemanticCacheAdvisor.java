package com.redis.stockanalysisagent.semanticcache;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

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
        // PART 8 STEP 6:
        // Replace this method body with the advisor-based semantic cache snippet from the Part 8 guide.
        // The finished advisor should:
        // 1. bypass semantic lookup when BYPASS_CACHE is true
        // 2. resolve the semantic cache key from advisor context or the user message
        // 3. short circuit on cache hit with a synthetic DIRECT_RESPONSE payload
        // 4. mark the response context with CACHE_HIT and CACHE_KEY
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
