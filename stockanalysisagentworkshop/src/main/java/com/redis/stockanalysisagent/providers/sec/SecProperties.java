package com.redis.stockanalysisagent.providers.sec;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
@ConfigurationProperties(prefix = "stock-analysis.sec")
public class SecProperties {

    private URI dataBaseUrl = URI.create("https://data.sec.gov");
    private URI tickerFileUrl = URI.create("https://www.sec.gov/files/company_tickers.json");
    private String userAgent = "stock-analysis-agent-workshop workshop@example.com";

    public URI getDataBaseUrl() {
        return dataBaseUrl;
    }

    public void setDataBaseUrl(URI dataBaseUrl) {
        this.dataBaseUrl = dataBaseUrl;
    }

    public URI getTickerFileUrl() {
        return tickerFileUrl;
    }

    public void setTickerFileUrl(URI tickerFileUrl) {
        this.tickerFileUrl = tickerFileUrl;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
}
