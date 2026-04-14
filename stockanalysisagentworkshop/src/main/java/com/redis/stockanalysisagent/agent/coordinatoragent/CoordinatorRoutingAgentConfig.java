package com.redis.stockanalysisagent.agent.coordinatoragent;

import com.redis.stockanalysisagent.memory.LongTermMemoryAdvisor;
import com.redis.stockanalysisagent.semanticcache.SemanticCacheAdvisor;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CoordinatorRoutingAgentConfig {

    private static final String DEFAULT_PROMPT = """
            PART 4 TODO:
            Replace this placeholder with the coordinator default prompt from the Part 4 guide.
            """;

    @Bean("coordinatorChatClient")
    public ChatClient coordinatorChatClient(
            ChatModel chatModel,
            ChatMemory chatMemory,
            SemanticCacheAdvisor semanticCacheAdvisor,
            LongTermMemoryAdvisor longTermMemoryAdvisor
    ) {
        // PART 4 STEP 1B:
        // Replace the return statement below with the snippet from the Part 4 guide.
        // PART 8 STEP 8:
        // After Part 4 is complete, add semanticCacheAdvisor before longTermMemoryAdvisor
        // and keep native structured output enabled.
        return ChatClient.builder(chatModel).build();
    }
}
