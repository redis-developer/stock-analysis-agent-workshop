package com.redis.stockanalysisagent.agent.marketdataagent;

import com.redis.stockanalysisagent.integrations.marketdata.MarketDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class MarketDataAgent {

    private static final Logger log = LoggerFactory.getLogger(MarketDataAgent.class);

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

    private final MarketDataProvider marketDataProvider;
    private final ChatClient marketDataChatClient;

    public MarketDataAgent(
            MarketDataProvider marketDataProvider,
            MarketDataTools marketDataTools,
            Optional<ChatModel> chatModel
    ) {
        this.marketDataProvider = marketDataProvider;
        if (chatModel.isEmpty()) {
            this.marketDataChatClient = null;
            return;
        }

        this.marketDataChatClient = ChatClient.builder(chatModel.orElseThrow())
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .defaultTools(marketDataTools)
                .defaultSystem(DEFAULT_PROMPT)
                .build();
    }

    public MarketDataResult execute(String ticker) {
        return execute(ticker, "What is the current market data for %s?".formatted(ticker.toUpperCase()));
    }

    public MarketDataResult execute(String ticker, String question) {
        if (marketDataChatClient == null) {
            return fallbackResult(ticker);
        }

        try {
            ResponseEntity<ChatResponse, MarketDataResult> response = marketDataChatClient
                    .prompt()
                    .user(buildPrompt(ticker, question))
                    .call()
                    .responseEntity(MarketDataResult.class);

            MarketDataResult entity = response.entity();
            if (entity == null || entity.getFinalResponse() == null || entity.getFinishReason() != MarketDataResult.FinishReason.COMPLETED) {
                return fallbackResult(ticker);
            }

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
        MarketSnapshot snapshot = marketDataProvider.fetchSnapshot(ticker);
        return MarketDataResult.completed(defaultDirectAnswer(snapshot), snapshot);
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
