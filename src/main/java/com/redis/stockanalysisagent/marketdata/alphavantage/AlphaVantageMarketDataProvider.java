package com.redis.stockanalysisagent.marketdata.alphavantage;

import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import com.redis.stockanalysisagent.marketdata.MarketDataProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import tools.jackson.databind.JsonNode;

@Component
@ConditionalOnProperty(
        prefix = "stock-analysis.market-data",
        name = "provider",
        havingValue = "alpha-vantage"
)
public class AlphaVantageMarketDataProvider implements MarketDataProvider {

    private static final String QUOTE_NODE = "Global Quote";

    private final RestClient restClient;
    private final AlphaVantageProperties properties;

    public AlphaVantageMarketDataProvider(
            RestClient.Builder restClientBuilder,
            AlphaVantageProperties properties
    ) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl().toString())
                .build();

        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("""
                    Alpha Vantage market data is enabled, but no API key is configured.
                    Set ALPHA_VANTAGE_API_KEY or stock-analysis.market-data.alpha-vantage.api-key.
                    """.stripIndent().trim());
        }
    }

    @Override
    public MarketSnapshot fetchSnapshot(String ticker) {
        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/query")
                        .queryParam("function", "GLOBAL_QUOTE")
                        .queryParam("symbol", ticker.toUpperCase())
                        .queryParam("apikey", properties.getApiKey())
                        .build())
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException("Alpha Vantage returned an empty response.");
        }

        if (response.hasNonNull("Error Message")) {
            throw new IllegalStateException("Alpha Vantage error: " + response.path("Error Message").asText());
        }

        if (response.hasNonNull("Note")) {
            throw new IllegalStateException("Alpha Vantage note: " + response.path("Note").asText());
        }

        JsonNode quote = response.path(QUOTE_NODE);
        if (quote.isMissingNode() || quote.size() == 0) {
            throw new IllegalStateException("Alpha Vantage did not return quote data for ticker " + ticker.toUpperCase() + ".");
        }

        BigDecimal currentPrice = moneyField(quote, "05. price");
        BigDecimal previousClose = moneyField(quote, "08. previous close");
        BigDecimal absoluteChange = moneyField(quote, "09. change");
        BigDecimal percentChange = percentField(quote, "10. change percent");
        String symbol = textField(quote, "01. symbol");
        OffsetDateTime asOf = latestTradingDay(quote.path("07. latest trading day").asText());

        return new MarketSnapshot(
                symbol,
                currentPrice,
                previousClose,
                absoluteChange,
                percentChange,
                asOf,
                "alpha-vantage"
        );
    }

    private BigDecimal moneyField(JsonNode quote, String fieldName) {
        return new BigDecimal(textField(quote, fieldName)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentField(JsonNode quote, String fieldName) {
        String value = textField(quote, fieldName).replace("%", "").trim();
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }

    private String textField(JsonNode quote, String fieldName) {
        String value = quote.path(fieldName).asText();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Alpha Vantage response is missing field " + fieldName + ".");
        }
        return value.trim();
    }

    private OffsetDateTime latestTradingDay(String latestTradingDay) {
        if (latestTradingDay == null || latestTradingDay.isBlank()) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }

        return LocalDate.parse(latestTradingDay).atStartOfDay().atOffset(ZoneOffset.UTC);
    }
}
