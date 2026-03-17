package com.redis.stockanalysisagent.agent.marketdataagent;

import com.redis.stockanalysisagent.integrations.marketdata.MarketDataProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class MarketDataTools {

    private final MarketDataProvider marketDataProvider;

    public MarketDataTools(MarketDataProvider marketDataProvider) {
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
