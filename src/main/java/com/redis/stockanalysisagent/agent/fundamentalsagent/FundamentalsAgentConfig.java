package com.redis.stockanalysisagent.agent.fundamentalsagent;

import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import com.redis.stockanalysisagent.fundamentals.FundamentalsProvider;
import com.redis.stockanalysisagent.agent.tools.FundamentalsTools;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;
import java.util.function.Function;

@Configuration
public class FundamentalsAgentConfig {

    private static final String DEFAULT_PROMPT = """
            ROLE
            You are the Fundamentals Agent for a stock-analysis system.

            RESPONSIBILITY
            Use the available tool to fetch a grounded fundamentals snapshot for the requested ticker and return a concise investor-focused result.

            RULES
            - Always use the fundamentals tool before returning a completed result.
            - Never invent revenue, income, margins, valuation ratios, filing dates, or source fields.
            - Use the exact tool result to populate finalResponse.
            - message should answer the user's question in plain language and stay concise.
            - Return valid JSON matching the requested schema.

            COMPLETION
            - Return finishReason = COMPLETED when finalResponse is available.
            - Return finishReason = ERROR only when the task cannot be completed.
            """;

    @Bean("fundamentalsChatClientFactory")
    public Function<Optional<MarketSnapshot>, ChatClient> fundamentalsChatClientFactory(
            ChatModel chatModel,
            FundamentalsProvider fundamentalsProvider
    ) {
        return marketSnapshot -> ChatClient.builder(chatModel)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .defaultTools(new FundamentalsTools(fundamentalsProvider, marketSnapshot))
                .defaultSystem(DEFAULT_PROMPT)
                .build();
    }
}
