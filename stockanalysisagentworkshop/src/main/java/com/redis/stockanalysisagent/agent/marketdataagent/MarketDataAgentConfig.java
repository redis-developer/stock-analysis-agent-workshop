package com.redis.stockanalysisagent.agent.marketdataagent;

import com.redis.stockanalysisagent.agent.tools.MarketDataTools;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MarketDataAgentConfig {

    private static final String DEFAULT_PROMPT = """
            PART 2 TODO:
            Replace this placeholder with the default prompt snippet from the Part 2 guide.
            """;

    @Bean("marketDataChatClient")
    public ChatClient marketDataChatClient(ChatModel chatModel, MarketDataTools marketDataTools) {
        // PART 2 STEP 2:
        // Replace the return statement below with the snippet from the Part 2 guide.
        return ChatClient.builder(chatModel)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .build();
    }
}
