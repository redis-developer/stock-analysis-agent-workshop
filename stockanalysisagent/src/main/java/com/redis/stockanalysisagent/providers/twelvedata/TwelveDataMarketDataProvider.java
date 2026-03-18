package com.redis.stockanalysisagent.providers.twelvedata;

import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import com.redis.stockanalysisagent.cache.CacheNames;
import com.redis.stockanalysisagent.cache.ExternalDataCache;
import com.redis.stockanalysisagent.cache.RedisCacheValueSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
@ConditionalOnProperty(
        prefix = "stock-analysis.market-data",
        name = "provider",
        havingValue = "twelve-data"
)
public class TwelveDataMarketDataProvider {

    private final RestClient restClient;
    private final TwelveDataProperties properties;
    private final ExternalDataCache externalDataCache;

    public TwelveDataMarketDataProvider(
            RestClient.Builder restClientBuilder,
            TwelveDataProperties properties,
            ExternalDataCache externalDataCache
    ) {
        this.properties = properties;
        this.externalDataCache = externalDataCache;
        this.restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl().toString())
                .build();
    }

    public MarketSnapshot fetchSnapshot(String ticker) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("""
                    Twelve Data market data is enabled, but no API key is configured.
                    Set TWELVE_DATA_API_KEY or stock-analysis.market-data.twelve-data.api-key.
                    """.stripIndent().trim());
        }

        Object cachedPayload = externalDataCache.getOrLoad(
                CacheNames.MARKET_DATA_QUOTES,
                ticker.toUpperCase(),
                () -> {
                    JsonNode response = restClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/quote")
                                    .queryParam("symbol", ticker.toUpperCase())
                                    .queryParam("apikey", properties.getApiKey())
                                    .build())
                            .retrieve()
                            .body(JsonNode.class);

                    if (response == null) {
                        throw new IllegalStateException("Twelve Data returned an empty response.");
                    }

                    if ("error".equalsIgnoreCase(optionalText(response, "status"))) {
                        throw new IllegalStateException("Twelve Data error: " + optionalText(response, "message", "Unknown error"));
                    }

                    BigDecimal currentPrice = moneyField(response, "close");
                    BigDecimal previousClose = moneyField(response, "previous_close");
                    BigDecimal absoluteChange = moneyField(response, "change");
                    BigDecimal percentChange = percentField(response, "percent_change");
                    String symbol = textField(response, "symbol");
                    OffsetDateTime asOf = timestamp(response);

                    return new MarketSnapshot(
                            symbol,
                            currentPrice,
                            previousClose,
                            absoluteChange,
                            percentChange,
                            asOf,
                            "twelve-data"
                    );
                }
        );

        return RedisCacheValueSupport.normalize(
                cachedPayload,
                MarketSnapshot.class,
                "Cached market snapshot for ticker " + ticker.toUpperCase()
        );
    }

    private BigDecimal moneyField(JsonNode response, String fieldName) {
        return new BigDecimal(textField(response, fieldName)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentField(JsonNode response, String fieldName) {
        return new BigDecimal(textField(response, fieldName).replace("%", "").trim())
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String textField(JsonNode response, String fieldName) {
        String value = optionalText(response, fieldName);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Twelve Data response is missing field " + fieldName + ".");
        }
        return value.trim();
    }

    private OffsetDateTime timestamp(JsonNode response) {
        JsonNode timestamp = response.get("timestamp");
        if (timestamp != null && !timestamp.isNull() && !timestamp.asText().isBlank()) {
            return OffsetDateTime.ofInstant(Instant.ofEpochSecond(timestamp.asLong()), ZoneOffset.UTC);
        }

        String datetime = optionalText(response, "datetime");
        if (datetime == null || datetime.isBlank()) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }

        return LocalDateTime.parse(datetime.trim().replace(" ", "T")).atOffset(ZoneOffset.UTC);
    }

    private String optionalText(JsonNode response, String fieldName) {
        return optionalText(response, fieldName, "");
    }

    private String optionalText(JsonNode response, String fieldName, String defaultValue) {
        JsonNode field = response.get(fieldName);
        if (field == null || field.isNull() || field.isMissingNode()) {
            return defaultValue;
        }
        return field.asText(defaultValue);
    }
}
