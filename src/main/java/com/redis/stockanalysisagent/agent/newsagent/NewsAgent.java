package com.redis.stockanalysisagent.agent.newsagent;

import com.redis.stockanalysisagent.news.NewsProvider;
import com.redis.stockanalysisagent.news.tavily.TavilyNewsProvider;
import com.redis.stockanalysisagent.news.tavily.TavilyNewsSearchResult;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class NewsAgent {

    private final NewsProvider officialNewsProvider;
    private final TavilyNewsProvider tavilyNewsProvider;

    public NewsAgent(NewsProvider officialNewsProvider, TavilyNewsProvider tavilyNewsProvider) {
        this.officialNewsProvider = officialNewsProvider;
        this.tavilyNewsProvider = tavilyNewsProvider;
    }

    public NewsResult execute(String ticker, String question) {
        NewsSnapshot officialSnapshot = officialNewsProvider.fetchSnapshot(ticker);
        TavilyNewsSearchResult webResult = tavilyNewsProvider.search(
                ticker,
                officialSnapshot.companyName(),
                question
        );

        return NewsResult.completed(new NewsSnapshot(
                officialSnapshot.ticker(),
                officialSnapshot.companyName(),
                officialSnapshot.officialItems(),
                webResult.items(),
                webResult.answer(),
                webResult.items().isEmpty() ? officialSnapshot.source() : officialSnapshot.source() + "+tavily"
        ));
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
}
