package com.redis.stockanalysisagent.providers.sec;

public record SecCompanyReference(
        String ticker,
        String companyName,
        String cik
) {
}
