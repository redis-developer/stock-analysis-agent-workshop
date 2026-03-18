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

    // PART 2 STEP 1:
    // Replace this comment with the getMarketSnapshot(...) tool method from the Part 2 guide.
}
