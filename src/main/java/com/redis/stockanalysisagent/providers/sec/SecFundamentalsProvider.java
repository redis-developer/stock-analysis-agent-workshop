package com.redis.stockanalysisagent.providers.sec;

import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsSnapshot;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import com.redis.stockanalysisagent.cache.CacheNames;
import com.redis.stockanalysisagent.cache.ExternalDataCache;
import com.redis.stockanalysisagent.fundamentals.FundamentalsProvider;
import com.redis.stockanalysisagent.providers.sec.SecCompanyReference;
import com.redis.stockanalysisagent.providers.sec.SecJsonNodeSupport;
import com.redis.stockanalysisagent.providers.sec.SecTickerLookupService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@ConditionalOnProperty(
        prefix = "stock-analysis.fundamentals",
        name = "provider",
        havingValue = "sec",
        matchIfMissing = true
)
public class SecFundamentalsProvider implements FundamentalsProvider {

    private static final Set<String> ANNUAL_FORMS = Set.of("10-K", "10-K/A", "20-F", "20-F/A", "40-F", "40-F/A");

    private final RestClient companyFactsRestClient;
    private final SecTickerLookupService secTickerLookupService;
    private final ExternalDataCache externalDataCache;

    public SecFundamentalsProvider(
            RestClient.Builder restClientBuilder,
            SecProperties properties,
            SecTickerLookupService secTickerLookupService,
            ExternalDataCache externalDataCache
    ) {
        this.secTickerLookupService = secTickerLookupService;
        this.externalDataCache = externalDataCache;

        RestClient.Builder configuredBuilder = restClientBuilder
                .defaultHeader("User-Agent", properties.getUserAgent())
                .defaultHeader("Accept-Encoding", "gzip, deflate");

        this.companyFactsRestClient = configuredBuilder
                .baseUrl(properties.getDataBaseUrl().toString())
                .build();
    }

    @Override
    public FundamentalsSnapshot fetchSnapshot(String ticker, Optional<MarketSnapshot> marketSnapshot) {
        SecCompanyReference companyReference = secTickerLookupService.resolve(ticker);
        JsonNode companyFacts = fetchCompanyFacts(companyReference);

        JsonNode facts = companyFacts.path("facts");
        SecFactValue latestRevenue = latestAnnualFact(facts, "us-gaap", List.of(
                "RevenueFromContractWithCustomerExcludingAssessedTax",
                "SalesRevenueNet",
                "Revenues"
        ), "USD");
        SecFactValue previousRevenue = previousAnnualFact(facts, "us-gaap", List.of(
                "RevenueFromContractWithCustomerExcludingAssessedTax",
                "SalesRevenueNet",
                "Revenues"
        ), "USD");
        SecFactValue netIncome = latestAnnualFact(facts, "us-gaap", List.of("NetIncomeLoss"), "USD");
        SecFactValue operatingIncome = latestAnnualFact(facts, "us-gaap", List.of("OperatingIncomeLoss"), "USD");
        SecFactValue cash = latestAnnualOrInstantFact(facts, "us-gaap", List.of("CashAndCashEquivalentsAtCarryingValue"), "USD");
        SecFactValue longTermDebt = latestAnnualOrInstantFact(facts, "us-gaap", List.of(
                "LongTermDebtAndFinanceLeaseObligations",
                "LongTermDebtNoncurrent",
                "LongTermDebt"
        ), "USD");
        SecFactValue sharesOutstanding = latestAnnualOrInstantFact(facts, "dei", List.of("EntityCommonStockSharesOutstanding"), "shares");
        SecFactValue dilutedEps = latestAnnualFact(facts, "us-gaap", List.of("EarningsPerShareDiluted"), "USD/shares");

        BigDecimal revenueGrowthPercent = growthPercent(latestRevenue, previousRevenue);
        BigDecimal operatingMarginPercent = marginPercent(operatingIncome, latestRevenue);
        BigDecimal netMarginPercent = marginPercent(netIncome, latestRevenue);
        BigDecimal currentPrice = marketSnapshot.map(MarketSnapshot::currentPrice).orElse(null);
        BigDecimal marketCap = marketCap(sharesOutstanding, currentPrice);
        BigDecimal priceToSales = ratio(marketCap, latestRevenue);
        BigDecimal priceToEarnings = currentPrice != null && dilutedEps != null && dilutedEps.value().compareTo(BigDecimal.ZERO) > 0
                ? currentPrice.divide(dilutedEps.value(), 2, RoundingMode.HALF_UP)
                : null;
        LocalDate fiscalYearEnd = latestAvailableEnd(latestRevenue, netIncome, operatingIncome);
        LocalDate filedAt = latestAvailableFiled(latestRevenue, netIncome, operatingIncome, cash, longTermDebt, sharesOutstanding);

        return new FundamentalsSnapshot(
                companyReference.ticker(),
                companyReference.companyName(),
                companyReference.cik(),
                valueOf(latestRevenue),
                valueOf(previousRevenue),
                revenueGrowthPercent,
                valueOf(netIncome),
                valueOf(operatingIncome),
                operatingMarginPercent,
                netMarginPercent,
                valueOf(cash),
                valueOf(longTermDebt),
                valueOf(sharesOutstanding),
                currentPrice,
                marketCap,
                priceToSales,
                valueOf(dilutedEps),
                priceToEarnings,
                fiscalYearEnd,
                filedAt,
                "sec"
        );
    }

    private JsonNode fetchCompanyFacts(SecCompanyReference companyReference) {
        Object cachedPayload = externalDataCache.getOrLoad(
                CacheNames.SEC_COMPANY_FACTS,
                companyReference.cik(),
                () -> {
                    JsonNode payload = companyFactsRestClient.get()
                            .uri("/api/xbrl/companyfacts/CIK{cik}.json", companyReference.cik())
                            .retrieve()
                            .body(JsonNode.class);

                    if (payload == null || payload.size() == 0) {
                        throw new IllegalStateException("SEC company facts returned an empty response for " + companyReference.ticker() + ".");
                    }

                    return payload;
                }
        );
        return SecJsonNodeSupport.normalize(cachedPayload, "SEC company facts for " + companyReference.ticker());
    }

    private SecFactValue latestAnnualFact(JsonNode facts, String taxonomy, List<String> concepts, String unitKey) {
        return annualFacts(facts, taxonomy, concepts, unitKey).stream()
                .max(Comparator.comparing(SecFactValue::filed).thenComparing(SecFactValue::end))
                .orElse(null);
    }

    private SecFactValue previousAnnualFact(JsonNode facts, String taxonomy, List<String> concepts, String unitKey) {
        List<SecFactValue> values = annualFacts(facts, taxonomy, concepts, unitKey).stream()
                .sorted(Comparator.comparing(SecFactValue::filed).thenComparing(SecFactValue::end).reversed())
                .toList();

        return values.size() > 1 ? values.get(1) : null;
    }

    private SecFactValue latestAnnualOrInstantFact(JsonNode facts, String taxonomy, List<String> concepts, String unitKey) {
        return concepts.stream()
                .map(concept -> facts.path(taxonomy).path(concept).path("units").path(unitKey))
                .filter(JsonNode::isArray)
                .flatMap(node -> node.valueStream())
                .map(this::toFactValue)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(this::isAnnualOrInstantFiling)
                .max(Comparator.comparing(SecFactValue::filed).thenComparing(SecFactValue::end))
                .orElse(null);
    }

    private List<SecFactValue> annualFacts(JsonNode facts, String taxonomy, List<String> concepts, String unitKey) {
        return concepts.stream()
                .map(concept -> facts.path(taxonomy).path(concept).path("units").path(unitKey))
                .filter(JsonNode::isArray)
                .flatMap(node -> node.valueStream())
                .map(this::toFactValue)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(this::isAnnualDurationFact)
                .sorted(Comparator.comparing(SecFactValue::filed).thenComparing(SecFactValue::end))
                .toList();
    }

    private Optional<SecFactValue> toFactValue(JsonNode node) {
        String valueText = textOrBlank(node, "val");
        String endText = textOrBlank(node, "end");
        String filedText = textOrBlank(node, "filed");
        String form = textOrBlank(node, "form");
        String fp = textOrBlank(node, "fp");
        String startText = textOrBlank(node, "start");

        if (valueText == null || valueText.isBlank() || endText == null || endText.isBlank() || filedText == null || filedText.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new SecFactValue(
                    new BigDecimal(valueText).setScale(2, RoundingMode.HALF_UP),
                    LocalDate.parse(endText),
                    LocalDate.parse(filedText),
                    form,
                    fp,
                    startText == null || startText.isBlank() ? null : LocalDate.parse(startText)
            ));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private boolean isAnnualDurationFact(SecFactValue factValue) {
        if (!isAnnualOrInstantFiling(factValue)) {
            return false;
        }

        if (factValue.start() == null) {
            return "FY".equalsIgnoreCase(factValue.filingPeriod());
        }

        long days = factValue.end().toEpochDay() - factValue.start().toEpochDay();
        return "FY".equalsIgnoreCase(factValue.filingPeriod()) || (days >= 300 && days <= 380);
    }

    private boolean isAnnualOrInstantFiling(SecFactValue factValue) {
        return ANNUAL_FORMS.contains(factValue.form());
    }

    private BigDecimal growthPercent(SecFactValue latest, SecFactValue previous) {
        if (latest == null || previous == null || previous.value().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        return latest.value()
                .subtract(previous.value())
                .divide(previous.value(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal marginPercent(SecFactValue numerator, SecFactValue denominator) {
        if (numerator == null || denominator == null || denominator.value().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        return numerator.value()
                .divide(denominator.value(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal marketCap(SecFactValue sharesOutstanding, BigDecimal currentPrice) {
        if (sharesOutstanding == null || currentPrice == null) {
            return null;
        }

        return sharesOutstanding.value()
                .multiply(currentPrice)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(BigDecimal numerator, SecFactValue denominator) {
        if (numerator == null || denominator == null || denominator.value().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        return numerator.divide(denominator.value(), 2, RoundingMode.HALF_UP);
    }

    private LocalDate latestAvailableEnd(SecFactValue... values) {
        return latestAvailable(values, SecFactValue::end);
    }

    private LocalDate latestAvailableFiled(SecFactValue... values) {
        return latestAvailable(values, SecFactValue::filed);
    }

    private LocalDate latestAvailable(SecFactValue[] values, java.util.function.Function<SecFactValue, LocalDate> extractor) {
        return Arrays.stream(values)
                .filter(java.util.Objects::nonNull)
                .map(extractor)
                .max(LocalDate::compareTo)
                .orElse(null);
    }

    private BigDecimal valueOf(SecFactValue factValue) {
        return factValue != null ? factValue.value() : null;
    }

    private String textOrBlank(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isMissingNode()) {
            return "";
        }

        return field.asText();
    }

    private record SecFactValue(
            BigDecimal value,
            LocalDate end,
            LocalDate filed,
            String form,
            String filingPeriod,
            LocalDate start
    ) {
    }
}
