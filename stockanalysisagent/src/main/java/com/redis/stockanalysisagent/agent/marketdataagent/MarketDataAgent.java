package com.redis.stockanalysisagent.agent.marketdataagent;

import com.redis.stockanalysisagent.agent.orchestration.TokenUsageSummary;
import com.redis.stockanalysisagent.providers.twelvedata.TwelveDataMarketDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class MarketDataAgent {

    private static final Logger log = LoggerFactory.getLogger(MarketDataAgent.class);
    private final TwelveDataMarketDataProvider marketDataProvider;
    private final ChatClient marketDataChatClient;

    public MarketDataAgent(
            TwelveDataMarketDataProvider marketDataProvider,
            @Qualifier("marketDataChatClient") ChatClient marketDataChatClient
    ) {
        this.marketDataProvider = marketDataProvider;
        this.marketDataChatClient = marketDataChatClient;
    }

    public MarketDataResult execute(String ticker) {
        return execute(ticker, "What is the current market data for %s?".formatted(ticker.toUpperCase()));
    }

    public MarketDataResult execute(String ticker, String question) {
        try {
            ResponseEntity<ChatResponse, MarketDataResult> response = marketDataChatClient
                    .prompt()
                    .user(buildPrompt(ticker, question))
                    .call()
                    .responseEntity(MarketDataResult.class);
            TokenUsageSummary tokenUsage = TokenUsageSummary.from(response.response());

            MarketDataResult entity = response.entity();
            if (entity == null || entity.getFinalResponse() == null || entity.getFinishReason() != MarketDataResult.FinishReason.COMPLETED) {
                return fallbackResult(ticker, tokenUsage);
            }
            entity.setTokenUsage(tokenUsage);

            if (entity.getMessage() == null || entity.getMessage().isBlank()) {
                entity.setMessage(defaultDirectAnswer(entity.getFinalResponse()));
            }

            return entity;
        } catch (RuntimeException ex) {
            log.warn("Falling back to deterministic market data execution because the tool-backed agent failed.", ex);
            return fallbackResult(ticker);
        }
    }

    public String createDirectAnswer(MarketSnapshot snapshot) {
        return defaultDirectAnswer(snapshot);
    }

    private MarketDataResult fallbackResult(String ticker) {
        return fallbackResult(ticker, null);
    }

    private MarketDataResult fallbackResult(String ticker, TokenUsageSummary tokenUsage) {
        MarketSnapshot snapshot = marketDataProvider.fetchSnapshot(ticker);
        MarketDataResult result = MarketDataResult.completed(defaultDirectAnswer(snapshot), snapshot);
        result.setTokenUsage(tokenUsage);
        return result;
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

    private String defaultDirectAnswer(MarketSnapshot snapshot) {
        return "%s is trading at $%s, up %s%% from the previous close."
                .formatted(
                        snapshot.symbol(),
                        snapshot.currentPrice(),
                        snapshot.percentChange()
                );
    }
}
