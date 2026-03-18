package com.redis.stockanalysisagent.agent.fundamentalsagent;

import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import com.redis.stockanalysisagent.agent.orchestration.TokenUsageSummary;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.function.Function;

@Service
public class FundamentalsAgent {

    private final Function<Optional<MarketSnapshot>, ChatClient> fundamentalsChatClientFactory;

    public FundamentalsAgent(
            @Qualifier("fundamentalsChatClientFactory")
            Function<Optional<MarketSnapshot>, ChatClient> fundamentalsChatClientFactory
    ) {
        this.fundamentalsChatClientFactory = fundamentalsChatClientFactory;
    }

    public FundamentalsResult execute(String ticker, String question) {
        return execute(ticker, question, Optional.empty());
    }

    public FundamentalsResult execute(String ticker, String question, MarketSnapshot marketSnapshot) {
        return execute(ticker, question, Optional.of(marketSnapshot));
    }

    private FundamentalsResult execute(String ticker, String question, Optional<MarketSnapshot> marketSnapshot) {
        ChatClient fundamentalsChatClient = fundamentalsChatClientFactory.apply(marketSnapshot);

        ResponseEntity<ChatResponse, FundamentalsResult> response = fundamentalsChatClient
                .prompt()
                .user(buildPrompt(ticker, question, marketSnapshot))
                .call()
                .responseEntity(FundamentalsResult.class);
        TokenUsageSummary tokenUsage = TokenUsageSummary.from(response.response());

        FundamentalsResult entity = response.entity();
        if (entity == null || entity.getFinalResponse() == null || entity.getFinishReason() != FundamentalsResult.FinishReason.COMPLETED) {
            throw new IllegalStateException("Fundamentals Agent returned an invalid response.");
        }
        entity.setTokenUsage(tokenUsage);
        return entity;
    }

    private String buildPrompt(String ticker, String question, Optional<MarketSnapshot> marketSnapshot) {
        String marketContext = marketSnapshot
                .map(snapshot -> """
                        AVAILABLE_MARKET_CONTEXT
                        - currentPrice: %s
                        - source: %s
                        """.formatted(snapshot.currentPrice(), snapshot.source()))
                .orElse("AVAILABLE_MARKET_CONTEXT\n- none supplied");

        return """
                TICKER
                %s

                USER_QUESTION
                %s

                %s

                INSTRUCTIONS
                - Use the fundamentals tool to fetch the normalized fundamentals snapshot.
                - If market context is available, the tool result may include valuation fields based on that context.
                - Populate finalResponse with the exact tool values.
                - message should directly answer the user's fundamentals question in one concise paragraph.
                """.formatted(ticker.toUpperCase(), question, marketContext);
    }
}
