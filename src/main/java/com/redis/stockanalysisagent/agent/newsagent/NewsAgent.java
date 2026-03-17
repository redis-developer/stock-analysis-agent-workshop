package com.redis.stockanalysisagent.agent.newsagent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class NewsAgent {

    private static final Logger log = LoggerFactory.getLogger(NewsAgent.class);

    private static final String DEFAULT_PROMPT = """
            ROLE
            You are the News Agent for a stock-analysis system.

            RESPONSIBILITY
            Use the available tool to fetch a grounded hybrid news snapshot for the requested ticker and return a concise investor-focused result.

            RULES
            - Always use the news tool before returning a completed result.
            - Never invent filings, headlines, publishers, dates, summaries, or sources.
            - Use the exact tool result to populate finalResponse.
            - message should answer the user's question in plain language and stay concise.
            - Return valid JSON matching the requested schema.

            COMPLETION
            - Return finishReason = COMPLETED when finalResponse is available.
            - Return finishReason = ERROR only when the task cannot be completed.
            """;

    private final NewsTools newsTools;
    private final ChatClient newsChatClient;

    public NewsAgent(NewsTools newsTools, Optional<ChatModel> chatModel) {
        this.newsTools = newsTools;
        if (chatModel.isEmpty()) {
            this.newsChatClient = null;
            return;
        }

        this.newsChatClient = ChatClient.builder(chatModel.orElseThrow())
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .defaultTools(newsTools)
                .defaultSystem(DEFAULT_PROMPT)
                .build();
    }

    public NewsResult execute(String ticker, String question) {
        if (newsChatClient == null) {
            return fallbackResult(ticker, question);
        }

        try {
            ResponseEntity<ChatResponse, NewsResult> response = newsChatClient
                    .prompt()
                    .user(buildPrompt(ticker, question))
                    .call()
                    .responseEntity(NewsResult.class);

            NewsResult entity = response.entity();
            if (entity == null || entity.getFinalResponse() == null || entity.getFinishReason() != NewsResult.FinishReason.COMPLETED) {
                return fallbackResult(ticker, question);
            }

            if (entity.getMessage() == null || entity.getMessage().isBlank()) {
                entity.setMessage(createDirectAnswer(entity.getFinalResponse()));
            }

            return entity;
        } catch (RuntimeException ex) {
            log.warn("Falling back to deterministic news execution because the tool-backed agent failed.", ex);
            return fallbackResult(ticker, question);
        }
    }

    public String createDirectAnswer(NewsSnapshot snapshot) {
        if (!snapshot.webItems().isEmpty()) {
            String highlights = snapshot.webItems().stream()
                    .limit(2)
                    .map(item -> "%s (%s)".formatted(item.title(), item.publisher()))
                    .collect(Collectors.joining("; "));

            String summaryPrefix = snapshot.webSummary() != null && !snapshot.webSummary().isBlank()
                    ? snapshot.webSummary().trim() + " "
                    : "";

            if (!snapshot.officialItems().isEmpty()) {
                NewsItem officialSignal = snapshot.officialItems().getFirst();
                return "%sRecent web coverage for %s includes %s. Official SEC signals also include %s filed on %s."
                        .formatted(
                                summaryPrefix,
                                snapshot.companyName(),
                                highlights,
                                officialSignal.label(),
                                officialSignal.publishedAt()
                        ).trim();
            }

            return "%sRecent web coverage for %s includes %s."
                    .formatted(summaryPrefix, snapshot.companyName(), highlights)
                    .trim();
        }

        if (snapshot.officialItems().isEmpty()) {
            return "No recent SEC filings or relevant web news were found for %s.".formatted(snapshot.ticker());
        }

        String highlights = snapshot.officialItems().stream()
                .limit(3)
                .map(item -> "%s filed on %s (%s)".formatted(item.label(), item.publishedAt(), item.title()))
                .collect(Collectors.joining("; "));

        return "Recent company-event signals for %s include %s."
                .formatted(snapshot.companyName(), highlights);
    }

    private NewsResult fallbackResult(String ticker, String question) {
        NewsSnapshot snapshot = newsTools.fetchNewsSnapshot(ticker, question);
        return NewsResult.completed(createDirectAnswer(snapshot), snapshot);
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
