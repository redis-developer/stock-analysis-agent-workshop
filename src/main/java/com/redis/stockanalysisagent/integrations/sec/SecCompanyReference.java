package com.redis.stockanalysisagent.integrations.sec;

public record SecCompanyReference(
        String ticker,
        String companyName,
        String cik
) {
}
