package com.redis.stockanalysisagent.agent.tools;

import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import com.redis.stockanalysisagent.providers.twelvedata.TwelveDataMarketDataProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class MarketDataTools {

    private final TwelveDataMarketDataProvider marketDataProvider;

    public MarketDataTools(TwelveDataMarketDataProvider marketDataProvider) {
        this.marketDataProvider = marketDataProvider;
    }

    @Tool(description = "Fetch the latest market snapshot for a stock ticker, including price, previous close, change, percent change, timestamp, and source.")
    public MarketSnapshot getMarketSnapshot(
            @ToolParam(description = "The stock ticker symbol in uppercase, for example AAPL.")
            String ticker
    ) {
        return marketDataProvider.fetchSnapshot(ticker);
    }
}
