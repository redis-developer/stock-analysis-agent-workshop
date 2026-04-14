package com.redis.stockanalysisagent.semanticcache;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SemanticCacheConfig {

    @Bean
    public SemanticCacheAdvisor semanticCacheAdvisor(SemanticAnalysisCache semanticAnalysisCache) {
        return new SemanticCacheAdvisor(semanticAnalysisCache);
    }
}
