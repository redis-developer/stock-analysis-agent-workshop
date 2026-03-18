package com.redis.stockanalysisagent.agent.newsagent;

import com.redis.stockanalysisagent.agent.orchestration.TokenUsageSummary;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class NewsAgent {

    private final ChatClient newsChatClient;

    public NewsAgent(@Qualifier("newsChatClient") ChatClient newsChatClient) {
        this.newsChatClient = newsChatClient;
    }

    public NewsResult execute(String ticker, String question) {
        ResponseEntity<ChatResponse, NewsResult> response = newsChatClient
                .prompt()
                .user(buildPrompt(ticker, question))
                .call()
                .responseEntity(NewsResult.class);
        TokenUsageSummary tokenUsage = TokenUsageSummary.from(response.response());

        NewsResult entity = response.entity();
        if (entity == null || entity.getFinalResponse() == null || entity.getFinishReason() != NewsResult.FinishReason.COMPLETED) {
            throw new IllegalStateException("News Agent returned an invalid response.");
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
                - Use the available tool to fetch a hybrid news snapshot for the ticker.
                - Populate finalResponse with the exact tool values.
                - message should directly answer the user's news question in one concise paragraph.
                """.formatted(ticker.toUpperCase(), question);
    }
}
