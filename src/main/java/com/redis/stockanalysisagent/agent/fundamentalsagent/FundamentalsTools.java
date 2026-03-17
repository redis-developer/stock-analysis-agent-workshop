package com.redis.stockanalysisagent.agent.fundamentalsagent;

import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import com.redis.stockanalysisagent.integrations.fundamentals.FundamentalsProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.Optional;

public class FundamentalsTools {

    private final FundamentalsProvider fundamentalsProvider;
    private final Optional<MarketSnapshot> marketSnapshot;

    public FundamentalsTools(
            FundamentalsProvider fundamentalsProvider,
            Optional<MarketSnapshot> marketSnapshot
    ) {
        this.fundamentalsProvider = fundamentalsProvider;
        this.marketSnapshot = marketSnapshot;
    }

    @Tool(description = "Fetch a normalized fundamentals snapshot for a stock ticker, including financial metrics, margins, valuation context, filing dates, and source.")
    public FundamentalsSnapshot getFundamentalsSnapshot(
            @ToolParam(description = "The stock ticker symbol in uppercase, for example AAPL.")
            String ticker
    ) {
        return fundamentalsProvider.fetchSnapshot(ticker, marketSnapshot);
    }
}
