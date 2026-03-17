package com.redis.stockanalysisagent.news;

import com.redis.stockanalysisagent.agent.newsagent.NewsSnapshot;

public interface NewsProvider {

    NewsSnapshot fetchSnapshot(String ticker);
}
