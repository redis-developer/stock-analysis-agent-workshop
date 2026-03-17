package com.redis.stockanalysisagent.integrations.fundamentals;

import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsSnapshot;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;

import java.util.Optional;

public interface FundamentalsProvider {

    FundamentalsSnapshot fetchSnapshot(String ticker, Optional<MarketSnapshot> marketSnapshot);
}
