package com.redis.stockanalysisagent.agent.technicalanalysisagent;

import com.redis.stockanalysisagent.agent.orchestration.TokenUsageSummary;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class TechnicalAnalysisAgent {

    private final ChatClient technicalAnalysisChatClient;

    public TechnicalAnalysisAgent(@Qualifier("technicalAnalysisChatClient") ChatClient technicalAnalysisChatClient) {
        this.technicalAnalysisChatClient = technicalAnalysisChatClient;
    }

    public TechnicalAnalysisResult execute(String ticker) {
        return execute(ticker, "What do the current technical signals look like for %s?".formatted(ticker.toUpperCase()));
    }

    public TechnicalAnalysisResult execute(String ticker, String question) {
        ResponseEntity<ChatResponse, TechnicalAnalysisResult> response = technicalAnalysisChatClient
                .prompt()
                .user(buildPrompt(ticker, question))
                .call()
                .responseEntity(TechnicalAnalysisResult.class);
        TokenUsageSummary tokenUsage = TokenUsageSummary.from(response.response());

        TechnicalAnalysisResult entity = response.entity();
        if (entity == null || entity.getFinalResponse() == null || entity.getFinishReason() != TechnicalAnalysisResult.FinishReason.COMPLETED) {
            throw new IllegalStateException("Technical Analysis Agent returned an invalid response.");
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
                - Use the available tool to fetch a current technical-analysis snapshot for the ticker.
                - Populate finalResponse with the exact tool values.
                - message should directly answer the user's technical question in one concise paragraph.
                """.formatted(ticker.toUpperCase(), question);
    }
}
