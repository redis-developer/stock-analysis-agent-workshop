package com.redis.stockanalysisagent.agent.fundamentalsagent;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FundamentalsSnapshot(
        String ticker,
        String companyName,
        String cik,
        BigDecimal revenue,
        BigDecimal previousRevenue,
        BigDecimal revenueGrowthPercent,
        BigDecimal netIncome,
        BigDecimal operatingIncome,
        BigDecimal operatingMarginPercent,
        BigDecimal netMarginPercent,
        BigDecimal cashAndCashEquivalents,
        BigDecimal longTermDebt,
        BigDecimal sharesOutstanding,
        BigDecimal currentPrice,
        BigDecimal marketCap,
        BigDecimal priceToSales,
        BigDecimal earningsPerShare,
        BigDecimal priceToEarnings,
        LocalDate fiscalYearEnd,
        LocalDate filedAt,
        String source
) {
}
