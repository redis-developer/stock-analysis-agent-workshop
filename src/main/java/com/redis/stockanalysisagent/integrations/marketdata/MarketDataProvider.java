package com.redis.stockanalysisagent.integrations.marketdata;

import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;

public interface MarketDataProvider {

    MarketSnapshot fetchSnapshot(String ticker);
}
