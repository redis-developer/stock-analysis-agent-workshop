package com.redis.stockanalysisagent.semanticguardrail;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SemanticGuardrailConfig {

    @Bean
    public SemanticGuardrailAdvisor semanticGuardrailAdvisor(SemanticGuardrailService semanticGuardrailService) {
        return new SemanticGuardrailAdvisor(semanticGuardrailService);
    }
}
