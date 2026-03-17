package com.redis.stockanalysisagent.integrations.news;

import com.redis.stockanalysisagent.agent.newsagent.NewsSnapshot;

public interface NewsProvider {

    NewsSnapshot fetchSnapshot(String ticker);
}
