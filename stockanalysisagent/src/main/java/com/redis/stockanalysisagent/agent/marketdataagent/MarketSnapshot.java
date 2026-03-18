package com.redis.stockanalysisagent.agent.marketdataagent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MarketSnapshot(
        String symbol,
        BigDecimal currentPrice,
        BigDecimal previousClose,
        BigDecimal absoluteChange,
        BigDecimal percentChange,
        OffsetDateTime asOf,
        String source
) {
}
