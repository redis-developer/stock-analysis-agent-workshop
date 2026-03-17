package com.redis.stockanalysisagent.agent.technicalanalysisagent;

import com.redis.stockanalysisagent.technicalanalysis.TechnicalAnalysisProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class TechnicalAnalysisAgent {

    private static final Logger log = LoggerFactory.getLogger(TechnicalAnalysisAgent.class);
    private final TechnicalAnalysisProvider technicalAnalysisProvider;
    private final ChatClient technicalAnalysisChatClient;

    public TechnicalAnalysisAgent(
            TechnicalAnalysisProvider technicalAnalysisProvider,
            @Qualifier("technicalAnalysisChatClient") ChatClient technicalAnalysisChatClient
    ) {
        this.technicalAnalysisProvider = technicalAnalysisProvider;
        this.technicalAnalysisChatClient = technicalAnalysisChatClient;
    }

    public TechnicalAnalysisResult execute(String ticker) {
        return execute(ticker, "What do the current technical signals look like for %s?".formatted(ticker.toUpperCase()));
    }

    public TechnicalAnalysisResult execute(String ticker, String question) {
        try {
            ResponseEntity<ChatResponse, TechnicalAnalysisResult> response = technicalAnalysisChatClient
                    .prompt()
                    .user(buildPrompt(ticker, question))
                    .call()
                    .responseEntity(TechnicalAnalysisResult.class);

            TechnicalAnalysisResult entity = response.entity();
            if (entity == null || entity.getFinalResponse() == null || entity.getFinishReason() != TechnicalAnalysisResult.FinishReason.COMPLETED) {
                return fallbackResult(ticker);
            }

            if (entity.getMessage() == null || entity.getMessage().isBlank()) {
                entity.setMessage(defaultDirectAnswer(entity.getFinalResponse()));
            }

            return entity;
        } catch (RuntimeException ex) {
            log.warn("Falling back to deterministic technical-analysis execution because the tool-backed agent failed.", ex);
            return fallbackResult(ticker);
        }
    }

    public String createDirectAnswer(TechnicalAnalysisSnapshot snapshot) {
        return defaultDirectAnswer(snapshot);
    }

    private TechnicalAnalysisResult fallbackResult(String ticker) {
        TechnicalAnalysisSnapshot snapshot = technicalAnalysisProvider.fetchSnapshot(ticker);
        return TechnicalAnalysisResult.completed(defaultDirectAnswer(snapshot), snapshot);
    }

    private String buildPrompt(String ticker, String question) {
        return """
                TICKER
                %s

                USER_QUESTION
                %s

                INSTRUCTIONS
                - Use the available tool to fetch a current technical-analysis snapshot for the ticker.
                - Populate finalResponse with the exact tool values.
                - message should directly answer the user's technical question in one concise paragraph.
                """.formatted(ticker.toUpperCase(), question);
    }

    private String defaultDirectAnswer(TechnicalAnalysisSnapshot snapshot) {
        return """
                Technical signals for %s are %s with %s momentum.
                The latest close is $%s versus the 20-day SMA of $%s and 20-day EMA of $%s, with RSI(14) at %s.
                """.formatted(
                snapshot.ticker(),
                snapshot.trendSignal().toLowerCase(),
                snapshot.momentumSignal().toLowerCase(),
                snapshot.latestClose(),
                snapshot.sma20(),
                snapshot.ema20(),
                snapshot.rsi14()
        ).replace('\n', ' ').trim();
    }
}
