package com.redis.stockanalysisagent.providers.twelvedata;

import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisSnapshot;
import com.redis.stockanalysisagent.cache.CacheNames;
import com.redis.stockanalysisagent.cache.ExternalDataCache;
import com.redis.stockanalysisagent.cache.RedisCacheValueSupport;
import com.redis.stockanalysisagent.providers.twelvedata.TwelveDataProperties;
import com.redis.stockanalysisagent.technicalanalysis.TechnicalAnalysisProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "stock-analysis.technical-analysis",
        name = "provider",
        havingValue = "twelve-data",
        matchIfMissing = true
)
public class TwelveDataTechnicalAnalysisProvider implements TechnicalAnalysisProvider {

    private final RestClient restClient;
    private final TwelveDataProperties twelveDataProperties;
    private final TechnicalAnalysisProperties technicalAnalysisProperties;
    private final ExternalDataCache externalDataCache;

    public TwelveDataTechnicalAnalysisProvider(
            RestClient.Builder restClientBuilder,
            TwelveDataProperties twelveDataProperties,
            TechnicalAnalysisProperties technicalAnalysisProperties,
            ExternalDataCache externalDataCache
    ) {
        this.twelveDataProperties = twelveDataProperties;
        this.technicalAnalysisProperties = technicalAnalysisProperties;
        this.externalDataCache = externalDataCache;
        this.restClient = restClientBuilder
                .baseUrl(twelveDataProperties.getBaseUrl().toString())
                .build();
    }

    @Override
    public TechnicalAnalysisSnapshot fetchSnapshot(String ticker) {
        if (twelveDataProperties.getApiKey() == null || twelveDataProperties.getApiKey().isBlank()) {
            throw new IllegalStateException("""
                    Twelve Data technical analysis is enabled, but no API key is configured.
                    Set TWELVE_DATA_API_KEY or stock-analysis.market-data.twelve-data.api-key.
                    """.stripIndent().trim());
        }

        String cacheKey = "%s|%s|%d|%d|%d|%d".formatted(
                ticker.toUpperCase(),
                technicalAnalysisProperties.getInterval(),
                technicalAnalysisProperties.getOutputSize(),
                technicalAnalysisProperties.getSmaPeriod(),
                technicalAnalysisProperties.getEmaPeriod(),
                technicalAnalysisProperties.getRsiPeriod()
        );

        Object cachedPayload = externalDataCache.getOrLoad(
                CacheNames.TECHNICAL_ANALYSIS_SNAPSHOTS,
                cacheKey,
                () -> {
                    JsonNode response = restClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/time_series")
                                    .queryParam("symbol", ticker.toUpperCase())
                                    .queryParam("interval", technicalAnalysisProperties.getInterval())
                                    .queryParam("outputsize", technicalAnalysisProperties.getOutputSize())
                                    .queryParam("order", "asc")
                                    .queryParam("apikey", twelveDataProperties.getApiKey())
                                    .build())
                            .retrieve()
                            .body(JsonNode.class);

                    if (response == null) {
                        throw new IllegalStateException("Twelve Data returned an empty time-series response.");
                    }

                    if ("error".equalsIgnoreCase(optionalText(response, "status", ""))) {
                        throw new IllegalStateException("Twelve Data error: " + optionalText(response, "message", "Unknown error"));
                    }

                    JsonNode valuesNode = response.path("values");
                    if (!valuesNode.isArray() || valuesNode.isEmpty()) {
                        throw new IllegalStateException("Twelve Data did not return time-series values for ticker " + ticker.toUpperCase() + ".");
                    }

                    List<BigDecimal> closes = new ArrayList<>();
                    OffsetDateTime asOf = null;
                    for (JsonNode valueNode : valuesNode) {
                        closes.add(new BigDecimal(requiredText(valueNode, "close")).setScale(2, RoundingMode.HALF_UP));
                        asOf = parseDateTime(requiredText(valueNode, "datetime"));
                    }

                    int requiredValues = Math.max(
                            technicalAnalysisProperties.getSmaPeriod(),
                            Math.max(technicalAnalysisProperties.getEmaPeriod(), technicalAnalysisProperties.getRsiPeriod() + 1)
                    );
                    if (closes.size() < requiredValues) {
                        throw new IllegalStateException("Twelve Data returned only %d values, but %d are required for technical analysis."
                                .formatted(closes.size(), requiredValues));
                    }

                    BigDecimal latestClose = closes.getLast();
                    BigDecimal sma20 = simpleMovingAverage(closes, technicalAnalysisProperties.getSmaPeriod());
                    BigDecimal ema20 = exponentialMovingAverage(closes, technicalAnalysisProperties.getEmaPeriod());
                    BigDecimal rsi14 = rsi(closes, technicalAnalysisProperties.getRsiPeriod());

                    return new TechnicalAnalysisSnapshot(
                            ticker.toUpperCase(),
                            technicalAnalysisProperties.getInterval(),
                            asOf != null ? asOf : OffsetDateTime.now(ZoneOffset.UTC),
                            latestClose,
                            sma20,
                            ema20,
                            rsi14,
                            trendSignal(latestClose, sma20, ema20),
                            momentumSignal(rsi14),
                            "twelve-data"
                    );
                }
        );

        return RedisCacheValueSupport.normalize(
                cachedPayload,
                TechnicalAnalysisSnapshot.class,
                "Cached technical-analysis snapshot for ticker " + ticker.toUpperCase()
        );
    }

    private BigDecimal simpleMovingAverage(List<BigDecimal> closes, int period) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = closes.size() - period; i < closes.size(); i++) {
            sum = sum.add(closes.get(i));
        }
        return sum.divide(BigDecimal.valueOf(period), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal exponentialMovingAverage(List<BigDecimal> closes, int period) {
        BigDecimal multiplier = BigDecimal.valueOf(2.0d / (period + 1.0d));
        BigDecimal ema = simpleMovingAverage(closes.subList(0, period), period);
        for (int i = period; i < closes.size(); i++) {
            BigDecimal close = closes.get(i);
            ema = close.subtract(ema)
                    .multiply(multiplier)
                    .add(ema);
        }
        return ema.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal rsi(List<BigDecimal> closes, int period) {
        BigDecimal gains = BigDecimal.ZERO;
        BigDecimal losses = BigDecimal.ZERO;
        for (int i = 1; i <= period; i++) {
            BigDecimal change = closes.get(i).subtract(closes.get(i - 1));
            if (change.signum() >= 0) {
                gains = gains.add(change);
            } else {
                losses = losses.add(change.abs());
            }
        }

        BigDecimal averageGain = gains.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
        BigDecimal averageLoss = losses.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);

        for (int i = period + 1; i < closes.size(); i++) {
            BigDecimal change = closes.get(i).subtract(closes.get(i - 1));
            BigDecimal gain = change.signum() > 0 ? change : BigDecimal.ZERO;
            BigDecimal loss = change.signum() < 0 ? change.abs() : BigDecimal.ZERO;

            averageGain = averageGain.multiply(BigDecimal.valueOf(period - 1))
                    .add(gain)
                    .divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
            averageLoss = averageLoss.multiply(BigDecimal.valueOf(period - 1))
                    .add(loss)
                    .divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
        }

        if (averageLoss.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal relativeStrength = averageGain.divide(averageLoss, 8, RoundingMode.HALF_UP);
        BigDecimal hundred = BigDecimal.valueOf(100);
        return hundred.subtract(
                hundred.divide(BigDecimal.ONE.add(relativeStrength), 8, RoundingMode.HALF_UP)
        ).setScale(2, RoundingMode.HALF_UP);
    }

    private String trendSignal(BigDecimal latestClose, BigDecimal sma, BigDecimal ema) {
        if (latestClose.compareTo(sma) > 0 && latestClose.compareTo(ema) > 0) {
            return "BULLISH";
        }

        if (latestClose.compareTo(sma) < 0 && latestClose.compareTo(ema) < 0) {
            return "BEARISH";
        }

        return "NEUTRAL";
    }

    private String momentumSignal(BigDecimal rsi) {
        if (rsi.compareTo(BigDecimal.valueOf(70)) >= 0) {
            return "OVERBOUGHT";
        }

        if (rsi.compareTo(BigDecimal.valueOf(30)) <= 0) {
            return "OVERSOLD";
        }

        return "NEUTRAL";
    }

    private String requiredText(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull() || field.isMissingNode() || field.asText().isBlank()) {
            throw new IllegalStateException("Twelve Data technical response is missing field " + fieldName + ".");
        }
        return field.asText();
    }

    private String optionalText(JsonNode node, String fieldName, String defaultValue) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull() || field.isMissingNode()) {
            return defaultValue;
        }
        return field.asText(defaultValue);
    }

    private OffsetDateTime parseDateTime(String value) {
        try {
            return OffsetDateTime.parse(value);
        } catch (RuntimeException ignored) {
        }

        try {
            return LocalDateTime.parse(value.replace(" ", "T")).atOffset(ZoneOffset.UTC);
        } catch (RuntimeException ignored) {
        }

        return LocalDate.parse(value).atStartOfDay().atOffset(ZoneOffset.UTC);
    }
}
