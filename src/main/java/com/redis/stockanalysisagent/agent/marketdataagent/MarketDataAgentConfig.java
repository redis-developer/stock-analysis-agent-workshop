package com.redis.stockanalysisagent.agent.marketdataagent;

import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MarketDataAgentConfig {

    private static final String DEFAULT_PROMPT = """
            ROLE
            You are the Market Data Agent for a stock-analysis system.

            RESPONSIBILITY
            Use the available tools to fetch current market data for the requested ticker and return a grounded result.

            RULES
            - Always use the market-data tools before returning a completed result.
            - Never invent prices, percentages, timestamps, or sources.
            - Use the exact tool result to populate finalResponse.
            - Keep message concise and directly useful to the user.
            - Return valid JSON matching the requested schema.

            COMPLETION
            - Return finishReason = COMPLETED when finalResponse is available.
            - Return finishReason = ERROR only when the task cannot be completed.
            """;

    @Bean("marketDataChatClient")
    public ChatClient marketDataChatClient(ChatModel chatModel, MarketDataTools marketDataTools) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .defaultTools(marketDataTools)
                .defaultSystem(DEFAULT_PROMPT)
                .build();
    }
}
