package com.redis.stockanalysisagent.agent.technicalanalysisagent;

import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TechnicalAnalysisAgentConfig {

    private static final String DEFAULT_PROMPT = """
            ROLE
            You are the Technical Analysis Agent for a stock-analysis system.

            RESPONSIBILITY
            Use the available tool to fetch a grounded technical-analysis snapshot for the requested ticker and return a concise trader-friendly result.

            RULES
            - Always use the technical-analysis tool before returning a completed result.
            - Never invent price levels, averages, RSI values, trend signals, momentum signals, timestamps, or sources.
            - Use the exact tool result to populate finalResponse.
            - message should answer the user's question in plain language and stay concise.
            - Return valid JSON matching the requested schema.

            COMPLETION
            - Return finishReason = COMPLETED when finalResponse is available.
            - Return finishReason = ERROR only when the task cannot be completed.
            """;

    @Bean("technicalAnalysisChatClient")
    public ChatClient technicalAnalysisChatClient(
            ChatModel chatModel,
            TechnicalAnalysisTools technicalAnalysisTools
    ) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .defaultTools(technicalAnalysisTools)
                .defaultSystem(DEFAULT_PROMPT)
                .build();
    }
}
