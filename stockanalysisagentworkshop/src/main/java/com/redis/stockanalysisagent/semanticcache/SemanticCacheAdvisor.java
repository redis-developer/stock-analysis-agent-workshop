package com.redis.stockanalysisagent.semanticcache;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

public class SemanticCacheAdvisor implements CallAdvisor {

    public static final String CACHE_HIT = "semantic_cache_hit";
    public static final String BYPASS_CACHE = "semantic_cache_bypass";
    private static final int DEFAULT_ORDER = 50;

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
        // 2. normalize the current user message into a cache key
        // 3. short circuit on cache hit with a synthetic DIRECT_RESPONSE payload
        // 4. mark the response context with CACHE_HIT
        return chain.nextCall(request).mutate()
                .context(CACHE_HIT, false)
                .build();
    }
}
