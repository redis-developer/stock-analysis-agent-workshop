package com.redis.stockanalysisagent.agent.fundamentalsagent;

import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FundamentalsAgentTest {

    @Test
    void fallsBackToDeterministicProviderWhenNoChatModelIsConfigured() {
        AtomicInteger providerCalls = new AtomicInteger();
        AtomicReference<Optional<MarketSnapshot>> observedMarketSnapshot = new AtomicReference<>(Optional.empty());

        FundamentalsSnapshot snapshot = new FundamentalsSnapshot(
                "AAPL",
                "Apple Inc.",
                "0000320193",
                new BigDecimal("400000000000.00"),
                new BigDecimal("380000000000.00"),
                new BigDecimal("5.26"),
                new BigDecimal("100000000000.00"),
                new BigDecimal("120000000000.00"),
                new BigDecimal("30.00"),
                new BigDecimal("25.00"),
                new BigDecimal("30000000000.00"),
                new BigDecimal("90000000000.00"),
                new BigDecimal("15000000000.00"),
                new BigDecimal("214.32"),
                new BigDecimal("3214800000000.00"),
                new BigDecimal("8.04"),
                new BigDecimal("6.50"),
                new BigDecimal("32.97"),
                LocalDate.parse("2024-09-28"),
                LocalDate.parse("2024-11-01"),
                "test-sec"
        );

        FundamentalsAgent agent = new FundamentalsAgent(
                (ticker, marketSnapshot) -> {
                    providerCalls.incrementAndGet();
                    observedMarketSnapshot.set(marketSnapshot);
                    return snapshot;
                },
                Optional.empty()
        );

        MarketSnapshot marketSnapshot = new MarketSnapshot(
                "AAPL",
                new BigDecimal("214.32"),
                new BigDecimal("211.01"),
                new BigDecimal("3.31"),
                new BigDecimal("1.57"),
                OffsetDateTime.parse("2026-03-16T00:00:00Z"),
                "test-market"
        );

        FundamentalsResult result = agent.execute("AAPL", marketSnapshot);

        assertThat(providerCalls).hasValue(1);
        assertThat(observedMarketSnapshot.get()).contains(marketSnapshot);
        assertThat(result.getFinishReason()).isEqualTo(FundamentalsResult.FinishReason.COMPLETED);
        assertThat(result.getFinalResponse()).isEqualTo(snapshot);
        assertThat(result.getMessage()).contains("Apple Inc. reported revenue");
    }
}
