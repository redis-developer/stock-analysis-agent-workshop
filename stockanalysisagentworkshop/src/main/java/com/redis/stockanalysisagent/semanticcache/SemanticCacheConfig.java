package com.redis.stockanalysisagent.semanticcache;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SemanticCacheConfig {

    @Bean
    public SemanticCacheAdvisor semanticCacheAdvisor(SemanticAnalysisCache semanticAnalysisCache) {
        // PART 8 STEP 7:
        // Keep this advisor bean in the semantic-cache package.
        return new SemanticCacheAdvisor(semanticAnalysisCache);
    }

    @Bean
    public SemanticCacheStoreAdvisor semanticCacheStoreAdvisor(SemanticAnalysisCache semanticAnalysisCache) {
        // PART 8 STEP 10:
        // Store completed synthesis answers through an advisor.
        return new SemanticCacheStoreAdvisor(semanticAnalysisCache);
    }
}
