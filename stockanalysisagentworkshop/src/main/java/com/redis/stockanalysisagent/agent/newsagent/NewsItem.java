package com.redis.stockanalysisagent.agent.newsagent;

import java.time.LocalDate;

public record NewsItem(
        LocalDate publishedAt,
        String publisher,
        String label,
        String title,
        String summary,
        String url
) {
}
