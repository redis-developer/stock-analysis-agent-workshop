package com.redis.stockanalysisagent.agent.marketdataagent;

import com.redis.stockanalysisagent.marketdata.MarketDataProvider;
import org.springframework.stereotype.Service;

@Service
public class MarketDataAgent {

    private final MarketDataProvider marketDataProvider;

    public MarketDataAgent(MarketDataProvider marketDataProvider) {
        this.marketDataProvider = marketDataProvider;
    }

    public MarketDataResult execute(String ticker) {
        return MarketDataResult.completed(marketDataProvider.fetchSnapshot(ticker));
    }

    public String createDirectAnswer(MarketSnapshot snapshot) {
        return "%s is trading at $%s, up %s%% from the previous close."
                .formatted(
                        snapshot.symbol(),
                        snapshot.currentPrice(),
                        snapshot.percentChange()
                );
    }
}
