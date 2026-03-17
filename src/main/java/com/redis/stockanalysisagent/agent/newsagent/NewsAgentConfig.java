package com.redis.stockanalysisagent.agent.newsagent;

import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NewsAgentConfig {

    private static final String DEFAULT_PROMPT = """
            ROLE
            You are the News Agent for a stock-analysis system.

            RESPONSIBILITY
            Use the available tool to fetch a grounded hybrid news snapshot for the requested ticker and return a concise investor-focused result.

            RULES
            - Always use the news tool before returning a completed result.
            - Never invent filings, headlines, publishers, dates, summaries, or sources.
            - Use the exact tool result to populate finalResponse.
            - message should answer the user's question in plain language and stay concise.
            - Return valid JSON matching the requested schema.

            COMPLETION
            - Return finishReason = COMPLETED when finalResponse is available.
            - Return finishReason = ERROR only when the task cannot be completed.
            """;

    @Bean("newsChatClient")
    public ChatClient newsChatClient(ChatModel chatModel, NewsTools newsTools) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .defaultTools(newsTools)
                .defaultSystem(DEFAULT_PROMPT)
                .build();
    }
}
