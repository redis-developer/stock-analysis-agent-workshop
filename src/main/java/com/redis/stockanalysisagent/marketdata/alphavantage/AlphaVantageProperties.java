package com.redis.stockanalysisagent.marketdata.alphavantage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
@ConfigurationProperties(prefix = "stock-analysis.market-data.alpha-vantage")
public class AlphaVantageProperties {

    private URI baseUrl = URI.create("https://www.alphavantage.co");
    private String apiKey = "";

    public URI getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
