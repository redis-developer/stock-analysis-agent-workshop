package com.redis.stockanalysisagent.agent.tools;

import com.redis.stockanalysisagent.agent.newsagent.NewsSnapshot;
import com.redis.stockanalysisagent.providers.sec.SecNewsProvider;
import com.redis.stockanalysisagent.providers.tavily.TavilyNewsProvider;
import com.redis.stockanalysisagent.providers.tavily.TavilyNewsSearchResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class NewsTools {

    private final SecNewsProvider officialNewsProvider;
    private final TavilyNewsProvider tavilyNewsProvider;

    public NewsTools(SecNewsProvider officialNewsProvider, TavilyNewsProvider tavilyNewsProvider) {
        this.officialNewsProvider = officialNewsProvider;
        this.tavilyNewsProvider = tavilyNewsProvider;
    }

    @Tool(description = "Fetch a hybrid news snapshot for a stock ticker, including official SEC filing signals, Tavily web-news results, an optional web summary, and source metadata.")
    public NewsSnapshot getNewsSnapshot(
            @ToolParam(description = "The stock ticker symbol in uppercase, for example AAPL.")
            String ticker,
            @ToolParam(description = "The user's investor-oriented news question, for example 'What recent news should I know about Apple?'.")
            String question
    ) {
        return fetchNewsSnapshot(ticker, question);
    }

    public NewsSnapshot fetchNewsSnapshot(String ticker, String question) {
        NewsSnapshot officialSnapshot = officialNewsProvider.fetchSnapshot(ticker);
        TavilyNewsSearchResult webResult = tavilyNewsProvider.search(
                ticker,
                officialSnapshot.companyName(),
                question
        );

        return new NewsSnapshot(
                officialSnapshot.ticker(),
                officialSnapshot.companyName(),
                officialSnapshot.officialItems(),
                webResult.items(),
                webResult.answer(),
                webResult.items().isEmpty() ? officialSnapshot.source() : officialSnapshot.source() + "+tavily"
        );
    }
}
