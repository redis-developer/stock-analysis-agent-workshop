package com.redis.stockanalysisagent.agent.technicalanalysisagent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TechnicalAnalysisSnapshot(
        String ticker,
        String interval,
        OffsetDateTime asOf,
        BigDecimal latestClose,
        BigDecimal sma20,
        BigDecimal ema20,
        BigDecimal rsi14,
        String trendSignal,
        String momentumSignal,
        String source
) {
}
