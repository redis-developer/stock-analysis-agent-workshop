package com.redis.stockanalysisagent.agent.tools;

import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisSnapshot;
import com.redis.stockanalysisagent.technicalanalysis.TechnicalAnalysisProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class TechnicalAnalysisTools {

    private final TechnicalAnalysisProvider technicalAnalysisProvider;

    public TechnicalAnalysisTools(TechnicalAnalysisProvider technicalAnalysisProvider) {
        this.technicalAnalysisProvider = technicalAnalysisProvider;
    }

    @Tool(description = "Fetch a normalized technical-analysis snapshot for a stock ticker, including latest close, SMA, EMA, RSI, trend signal, momentum signal, and source.")
    public TechnicalAnalysisSnapshot getTechnicalAnalysisSnapshot(
            @ToolParam(description = "The stock ticker symbol in uppercase, for example AAPL.")
            String ticker
    ) {
        return technicalAnalysisProvider.fetchSnapshot(ticker);
    }
}
