package com.redis.stockanalysisagent.integrations.marketdata.twelvedata;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
@ConfigurationProperties(prefix = "stock-analysis.market-data.twelve-data")
public class TwelveDataProperties {

    private URI baseUrl = URI.create("https://api.twelvedata.com");
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
