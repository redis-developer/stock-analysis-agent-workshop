package com.redis.stockanalysisagent.memory;

import com.redis.agentmemory.MemoryAPIClient;
import com.redis.stockanalysisagent.memory.service.AgentMemoryService;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentMemoryConfig {

    @Bean
    public MemoryAPIClient memoryAPIClient(
            @Value("${agent-memory.server.url}") String serverUrl,
            @Value("${agent-memory.server.namespace:stock-analysis}") String namespace
    ) {
        return MemoryAPIClient.builder(serverUrl)
                .defaultNamespace(namespace)
                .timeout(30.0)
                .build();
    }

    @Bean
    public AmsChatMemoryRepository amsChatMemoryRepository(
            AgentMemoryService agentMemoryService
    ) {
        return new AmsChatMemoryRepository(agentMemoryService);
    }

    @Bean
    public ChatMemory chatMemory(AmsChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(20)
                .build();
    }

    @Bean
    public LongTermMemoryAdvisor longTermMemoryAdvisor(
            AgentMemoryService agentMemoryService,
            AmsChatMemoryRepository memoryRepository
    ) {
        return new LongTermMemoryAdvisor(agentMemoryService, memoryRepository, 5);
    }
}
