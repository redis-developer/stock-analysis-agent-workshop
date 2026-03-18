package com.redis.stockanalysisagent.agent.marketdataagent;

import com.redis.stockanalysisagent.agent.orchestration.TokenUsageSummary;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class MarketDataAgent {

    private final ChatClient marketDataChatClient;

    public MarketDataAgent(@Qualifier("marketDataChatClient") ChatClient marketDataChatClient) {
        this.marketDataChatClient = marketDataChatClient;
    }

    public MarketDataResult execute(String ticker, String question) {
        ResponseEntity<ChatResponse, MarketDataResult> response = marketDataChatClient
                .prompt()
                .user(buildPrompt(ticker, question))
                .call()
                .responseEntity(MarketDataResult.class);
        TokenUsageSummary tokenUsage = TokenUsageSummary.from(response.response());

        MarketDataResult entity = response.entity();
        if (entity == null || entity.getFinalResponse() == null || entity.getFinishReason() != MarketDataResult.FinishReason.COMPLETED) {
            throw new IllegalStateException("Market Data Agent returned an invalid response.");
        }
        entity.setTokenUsage(tokenUsage);
        return entity;
    }

    private String buildPrompt(String ticker, String question) {
        return """
                TICKER
                %s

                USER_QUESTION
                %s

                INSTRUCTIONS
                - Use the available tool to fetch a current market snapshot for the ticker.
                - Populate finalResponse with the exact tool values.
                - message should directly answer the user's question in one concise sentence.
                """.formatted(ticker.toUpperCase(), question);
    }
}
