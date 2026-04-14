package com.redis.stockanalysisagent.semanticcache;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SemanticCacheConfig {

    @Bean
    public SemanticCacheAdvisor semanticCacheAdvisor(SemanticAnalysisCache semanticAnalysisCache) {
        // PART 8 STEP 7:
        // Keep the semantic cache advisor bean in the semantic-cache package so the
        // coordinator config can inject it without constructing it inline.
        return new SemanticCacheAdvisor(semanticAnalysisCache);
    }
}
