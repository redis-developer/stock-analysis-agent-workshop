package com.redis.stockanalysisagent.marketdata;

import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;

public interface MarketDataProvider {

    MarketSnapshot fetchSnapshot(String ticker);
}
