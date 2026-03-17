package com.redis.stockanalysisagent.providers.sec;

import com.redis.stockanalysisagent.cache.CacheNames;
import com.redis.stockanalysisagent.cache.ExternalDataCache;
import com.redis.stockanalysisagent.providers.sec.SecProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class SecTickerLookupService {

    private final RestClient tickerRestClient;
    private final SecProperties properties;
    private final ExternalDataCache externalDataCache;

    private volatile Map<String, SecCompanyReference> tickerIndex;

    public SecTickerLookupService(
            RestClient.Builder restClientBuilder,
            SecProperties properties,
            ExternalDataCache externalDataCache
    ) {
        this.properties = properties;
        this.externalDataCache = externalDataCache;
        this.tickerRestClient = restClientBuilder
                .defaultHeader("User-Agent", properties.getUserAgent())
                .defaultHeader("Accept-Encoding", "gzip, deflate")
                .build();
    }

    public SecCompanyReference resolve(String ticker) {
        Map<String, SecCompanyReference> localIndex = tickerIndex;
        if (localIndex == null) {
            synchronized (this) {
                if (tickerIndex == null) {
                    tickerIndex = normalizeTickerIndex(externalDataCache.getOrLoad(
                            CacheNames.SEC_TICKER_INDEX,
                            "all",
                            this::loadTickerIndex
                    ));
                }
                localIndex = tickerIndex;
            }
        }

        SecCompanyReference companyReference = localIndex.get(ticker.toUpperCase());
        if (companyReference == null) {
            throw new IllegalStateException("SEC ticker lookup returned no CIK for ticker " + ticker.toUpperCase() + ".");
        }

        return companyReference;
    }

    private Map<String, SecCompanyReference> loadTickerIndex() {
        JsonNode payload = tickerRestClient.get()
                .uri(properties.getTickerFileUrl())
                .retrieve()
                .body(JsonNode.class);

        if (payload == null || payload.size() == 0) {
            throw new IllegalStateException("SEC ticker lookup returned an empty response.");
        }

        Map<String, SecCompanyReference> companies = new LinkedHashMap<>();
        payload.properties().forEach(entry -> {
            JsonNode company = entry.getValue();
            String ticker = textOrBlank(company, "ticker");
            if (ticker == null || ticker.isBlank()) {
                return;
            }

            String cik = "%010d".formatted(longOrDefault(company, "cik_str", 0L));
            String title = textOrBlank(company, "title");
            companies.put(ticker.toUpperCase(), new SecCompanyReference(ticker.toUpperCase(), title, cik));
        });

        return Map.copyOf(companies);
    }

    private Map<String, SecCompanyReference> normalizeTickerIndex(Map<String, ?> cachedIndex) {
        if (cachedIndex == null || cachedIndex.isEmpty()) {
            return Map.of();
        }

        boolean needsNormalization = cachedIndex.values().stream()
                .filter(Objects::nonNull)
                .anyMatch(value -> !(value instanceof SecCompanyReference));

        if (!needsNormalization) {
            Map<String, SecCompanyReference> typedIndex = new LinkedHashMap<>();
            cachedIndex.forEach((ticker, value) -> typedIndex.put(ticker, (SecCompanyReference) value));
            return Map.copyOf(typedIndex);
        }

        Map<String, SecCompanyReference> normalized = new LinkedHashMap<>();
        cachedIndex.forEach((ticker, value) -> normalized.put(ticker, toCompanyReference(ticker, value)));
        return Map.copyOf(normalized);
    }

    private SecCompanyReference toCompanyReference(String tickerKey, Object value) {
        if (value instanceof SecCompanyReference reference) {
            return reference;
        }

        if (value instanceof Map<?, ?> map) {
            String ticker = stringValue(map.get("ticker"));
            String companyName = stringValue(map.get("companyName"));
            String cik = stringValue(map.get("cik"));

            String resolvedTicker = ticker != null && !ticker.isBlank()
                    ? ticker.toUpperCase()
                    : tickerKey.toUpperCase();

            if (cik == null || cik.isBlank()) {
                throw new IllegalStateException("Cached SEC ticker lookup entry for %s is missing a CIK.".formatted(resolvedTicker));
            }

            return new SecCompanyReference(resolvedTicker, companyName, cik);
        }

        throw new IllegalStateException(
                "Cached SEC ticker lookup entry for %s had unexpected type %s."
                        .formatted(tickerKey.toUpperCase(), value == null ? "null" : value.getClass().getName())
        );
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String textOrBlank(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isMissingNode()) {
            return "";
        }

        return field.asText("");
    }

    private long longOrDefault(JsonNode node, String fieldName, long defaultValue) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isMissingNode()) {
            return defaultValue;
        }

        return field.asLong(defaultValue);
    }
}
