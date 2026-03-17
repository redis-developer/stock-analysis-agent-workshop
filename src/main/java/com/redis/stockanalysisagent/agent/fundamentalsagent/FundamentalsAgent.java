package com.redis.stockanalysisagent.agent.fundamentalsagent;

import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import com.redis.stockanalysisagent.integrations.fundamentals.FundamentalsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
public class FundamentalsAgent {

    private static final Logger log = LoggerFactory.getLogger(FundamentalsAgent.class);

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

    private final FundamentalsProvider fundamentalsProvider;
    private final Optional<ChatModel> chatModel;

    public FundamentalsAgent(FundamentalsProvider fundamentalsProvider, Optional<ChatModel> chatModel) {
        this.fundamentalsProvider = fundamentalsProvider;
        this.chatModel = chatModel;
    }

    public FundamentalsResult execute(String ticker) {
        return execute(ticker, "What are the current fundamentals for %s?".formatted(ticker.toUpperCase()), Optional.empty());
    }

    public FundamentalsResult execute(String ticker, String question) {
        return execute(ticker, question, Optional.empty());
    }

    public FundamentalsResult execute(String ticker, MarketSnapshot marketSnapshot) {
        return execute(ticker, "What are the current fundamentals for %s?".formatted(ticker.toUpperCase()), Optional.of(marketSnapshot));
    }

    public FundamentalsResult execute(String ticker, String question, MarketSnapshot marketSnapshot) {
        return execute(ticker, question, Optional.of(marketSnapshot));
    }

    private FundamentalsResult execute(String ticker, String question, Optional<MarketSnapshot> marketSnapshot) {
        if (chatModel.isEmpty()) {
            return fallbackResult(ticker, marketSnapshot);
        }

        try {
            ChatClient fundamentalsChatClient = ChatClient.builder(chatModel.orElseThrow())
                    .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                    .defaultTools(new FundamentalsTools(fundamentalsProvider, marketSnapshot))
                    .defaultSystem(DEFAULT_PROMPT)
                    .build();

            ResponseEntity<ChatResponse, FundamentalsResult> response = fundamentalsChatClient
                    .prompt()
                    .user(buildPrompt(ticker, question, marketSnapshot))
                    .call()
                    .responseEntity(FundamentalsResult.class);

            FundamentalsResult entity = response.entity();
            if (entity == null || entity.getFinalResponse() == null || entity.getFinishReason() != FundamentalsResult.FinishReason.COMPLETED) {
                return fallbackResult(ticker, marketSnapshot);
            }

            if (entity.getMessage() == null || entity.getMessage().isBlank()) {
                entity.setMessage(defaultDirectAnswer(entity.getFinalResponse()));
            }

            return entity;
        } catch (RuntimeException ex) {
            log.warn("Falling back to deterministic fundamentals execution because the tool-backed agent failed.", ex);
            return fallbackResult(ticker, marketSnapshot);
        }
    }

    public String createDirectAnswer(FundamentalsSnapshot snapshot) {
        return defaultDirectAnswer(snapshot);
    }

    private FundamentalsResult fallbackResult(String ticker, Optional<MarketSnapshot> marketSnapshot) {
        FundamentalsSnapshot snapshot = fundamentalsProvider.fetchSnapshot(ticker, marketSnapshot);
        return FundamentalsResult.completed(defaultDirectAnswer(snapshot), snapshot);
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

    private String defaultDirectAnswer(FundamentalsSnapshot snapshot) {
        String revenueGrowth = formatPercent(snapshot.revenueGrowthPercent());
        String operatingMargin = formatPercent(snapshot.operatingMarginPercent());
        String netMargin = formatPercent(snapshot.netMarginPercent());

        return """
                %s reported revenue of %s, revenue growth of %s, and net income of %s.
                Operating margin was %s and net margin was %s.
                """.formatted(
                snapshot.companyName(),
                formatMoney(snapshot.revenue()),
                revenueGrowth,
                formatMoney(snapshot.netIncome()),
                operatingMargin,
                netMargin
        ).replace('\n', ' ').trim();
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) {
            return "unavailable";
        }

        BigDecimal billions = value.divide(BigDecimal.valueOf(1_000_000_000L), 2, RoundingMode.HALF_UP);
        return "$" + billions + "B";
    }

    private String formatPercent(BigDecimal value) {
        if (value == null) {
            return "unavailable";
        }

        return value.setScale(2, RoundingMode.HALF_UP) + "%";
    }
}
