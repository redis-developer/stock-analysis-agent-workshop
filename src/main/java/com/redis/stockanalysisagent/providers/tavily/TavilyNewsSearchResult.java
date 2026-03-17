package com.redis.stockanalysisagent.providers.tavily;

import com.redis.stockanalysisagent.agent.newsagent.NewsItem;

import java.util.List;

public record TavilyNewsSearchResult(
        List<NewsItem> items,
        String answer
) {
    public static TavilyNewsSearchResult empty() {
        return new TavilyNewsSearchResult(List.of(), null);
    }
}
