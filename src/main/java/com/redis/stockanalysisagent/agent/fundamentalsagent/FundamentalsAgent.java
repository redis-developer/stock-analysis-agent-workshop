package com.redis.stockanalysisagent.agent.fundamentalsagent;

import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import com.redis.stockanalysisagent.fundamentals.FundamentalsProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
public class FundamentalsAgent {

    private final FundamentalsProvider fundamentalsProvider;

    public FundamentalsAgent(FundamentalsProvider fundamentalsProvider) {
        this.fundamentalsProvider = fundamentalsProvider;
    }

    public FundamentalsResult execute(String ticker) {
        return execute(ticker, Optional.empty());
    }

    public FundamentalsResult execute(String ticker, MarketSnapshot marketSnapshot) {
        return execute(ticker, Optional.of(marketSnapshot));
    }

    private FundamentalsResult execute(String ticker, Optional<MarketSnapshot> marketSnapshot) {
        return FundamentalsResult.completed(fundamentalsProvider.fetchSnapshot(ticker, marketSnapshot));
    }

    public String createDirectAnswer(FundamentalsSnapshot snapshot) {
        String revenueGrowth = formatPercent(snapshot.revenueGrowthPercent());
        String operatingMargin = formatPercent(snapshot.operatingMarginPercent());
        String netMargin = formatPercent(snapshot.netMarginPercent());

        return """
                %s reported revenue of %s, revenue growth of %s, and net income of %s.
                Operating margin was %s and net margin was %s.
                """.formatted(
                snapshot.companyName(),
                formatMoney(snapshot.revenue()),
                revenueGrowth,
                formatMoney(snapshot.netIncome()),
                operatingMargin,
                netMargin
        ).replace('\n', ' ').trim();
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) {
            return "unavailable";
        }

        BigDecimal billions = value.divide(BigDecimal.valueOf(1_000_000_000L), 2, RoundingMode.HALF_UP);
        return "$" + billions + "B";
    }

    private String formatPercent(BigDecimal value) {
        if (value == null) {
            return "unavailable";
        }

        return value.setScale(2, RoundingMode.HALF_UP) + "%";
    }
}
