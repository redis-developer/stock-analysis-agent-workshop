package com.redis.stockanalysisagent.marketdata;

import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@Component
@ConditionalOnProperty(
        prefix = "stock-analysis.market-data",
        name = "provider",
        havingValue = "mock",
        matchIfMissing = true
)
public class MockMarketDataProvider implements MarketDataProvider {

    private static final Map<String, MarketSnapshot> SNAPSHOTS = Map.of(
            "AAPL", snapshot("AAPL", "214.32", "211.01"),
            "MSFT", snapshot("MSFT", "428.17", "423.44"),
            "NVDA", snapshot("NVDA", "118.42", "116.03"),
            "TSLA", snapshot("TSLA", "178.51", "181.77")
    );

    @Override
    public MarketSnapshot fetchSnapshot(String ticker) {
        return SNAPSHOTS.getOrDefault(ticker.toUpperCase(), snapshot(ticker.toUpperCase(), "100.00", "98.50"));
    }

    private static MarketSnapshot snapshot(String symbol, String currentPrice, String previousClose) {
        BigDecimal current = new BigDecimal(currentPrice);
        BigDecimal previous = new BigDecimal(previousClose);
        BigDecimal change = current.subtract(previous);
        BigDecimal percentChange = change
                .divide(previous, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, java.math.RoundingMode.HALF_UP);

        return new MarketSnapshot(
                symbol,
                current,
                previous,
                change.setScale(2, java.math.RoundingMode.HALF_UP),
                percentChange,
                OffsetDateTime.now(),
                "mock"
        );
    }
}
