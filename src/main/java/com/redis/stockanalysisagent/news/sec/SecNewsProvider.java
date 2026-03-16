package com.redis.stockanalysisagent.news.sec;

import com.redis.stockanalysisagent.agent.newsagent.NewsItem;
import com.redis.stockanalysisagent.agent.newsagent.NewsSnapshot;
import com.redis.stockanalysisagent.fundamentals.sec.SecProperties;
import com.redis.stockanalysisagent.news.NewsProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@ConditionalOnProperty(
        prefix = "stock-analysis.news",
        name = "provider",
        havingValue = "sec",
        matchIfMissing = true
)
public class SecNewsProvider implements NewsProvider {

    private static final Set<String> RELEVANT_FORMS = Set.of(
            "8-K", "8-K/A",
            "10-Q", "10-Q/A",
            "10-K", "10-K/A",
            "6-K", "6-K/A",
            "20-F", "20-F/A",
            "DEF 14A",
            "S-1", "S-1/A"
    );

    private final RestClient tickerRestClient;
    private final RestClient submissionsRestClient;
    private final SecProperties properties;

    private volatile Map<String, SecCompanyReference> tickerIndex;

    public SecNewsProvider(RestClient.Builder restClientBuilder, SecProperties properties) {
        this.properties = properties;
        RestClient.Builder configuredBuilder = restClientBuilder
                .defaultHeader("User-Agent", properties.getUserAgent())
                .defaultHeader("Accept-Encoding", "gzip, deflate");

        this.tickerRestClient = configuredBuilder.build();
        this.submissionsRestClient = configuredBuilder
                .baseUrl(properties.getDataBaseUrl().toString())
                .build();
    }

    @Override
    public NewsSnapshot fetchSnapshot(String ticker) {
        SecCompanyReference companyReference = resolveCompanyReference(ticker);
        JsonNode submissions = fetchSubmissions(companyReference);
        JsonNode recent = submissions.path("filings").path("recent");

        List<SecFiling> filings = extractFilings(recent, companyReference);

        return new NewsSnapshot(
                companyReference.ticker(),
                companyReference.companyName(),
                filings.stream()
                        .limit(5)
                        .map(filing -> new NewsItem(
                                filing.filedAt(),
                                "SEC",
                                filing.form(),
                                filing.title(),
                                filing.summary(),
                                filing.url()
                        ))
                        .toList(),
                List.of(),
                null,
                "sec"
        );
    }

    private SecCompanyReference resolveCompanyReference(String ticker) {
        Map<String, SecCompanyReference> localIndex = tickerIndex;
        if (localIndex == null) {
            synchronized (this) {
                if (tickerIndex == null) {
                    tickerIndex = loadTickerIndex();
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

    private JsonNode fetchSubmissions(SecCompanyReference companyReference) {
        JsonNode payload = submissionsRestClient.get()
                .uri("/submissions/CIK{cik}.json", companyReference.cik())
                .retrieve()
                .body(JsonNode.class);

        if (payload == null || payload.size() == 0) {
            throw new IllegalStateException("SEC submissions returned an empty response for " + companyReference.ticker() + ".");
        }

        return payload;
    }

    private List<SecFiling> extractFilings(JsonNode recent, SecCompanyReference companyReference) {
        int filingCount = recent.path("filingDate").size();
        List<SecFiling> filings = new ArrayList<>();
        for (int index = 0; index < filingCount; index++) {
            String form = arrayText(recent, "form", index);
            String filingDate = arrayText(recent, "filingDate", index);
            if (form == null || form.isBlank() || filingDate == null || filingDate.isBlank()) {
                continue;
            }

            if (!RELEVANT_FORMS.contains(form.toUpperCase())) {
                continue;
            }

            LocalDate filedAt;
            try {
                filedAt = LocalDate.parse(filingDate);
            } catch (RuntimeException ignored) {
                continue;
            }

            String accessionNumber = arrayText(recent, "accessionNumber", index);
            String primaryDocument = arrayText(recent, "primaryDocument", index);
            String primaryDocDescription = arrayText(recent, "primaryDocDescription", index);
            String items = arrayText(recent, "items", index);

            filings.add(new SecFiling(
                    filedAt,
                    form.toUpperCase(),
                    titleFor(form, primaryDocDescription),
                    summaryFor(form, items),
                    buildFilingUrl(companyReference.cik(), accessionNumber, primaryDocument)
            ));
        }

        return filings.stream()
                .sorted(Comparator.comparing(SecFiling::filedAt).reversed())
                .toList();
    }

    private String titleFor(String form, String primaryDocDescription) {
        if (primaryDocDescription != null && !primaryDocDescription.isBlank()) {
            return primaryDocDescription;
        }

        return switch (form.toUpperCase()) {
            case "8-K", "8-K/A" -> "Current report";
            case "10-Q", "10-Q/A" -> "Quarterly report";
            case "10-K", "10-K/A", "20-F", "20-F/A" -> "Annual report";
            case "6-K", "6-K/A" -> "Foreign issuer report";
            case "DEF 14A" -> "Proxy statement";
            case "S-1", "S-1/A" -> "Registration statement";
            default -> "SEC filing";
        };
    }

    private String summaryFor(String form, String items) {
        if (items != null && !items.isBlank()) {
            return "%s items: %s".formatted(form.toUpperCase(), items);
        }

        return switch (form.toUpperCase()) {
            case "8-K", "8-K/A" -> "Recent company update filed with the SEC.";
            case "10-Q", "10-Q/A" -> "Quarterly financial filing.";
            case "10-K", "10-K/A", "20-F", "20-F/A" -> "Annual financial filing.";
            case "6-K", "6-K/A" -> "Recent foreign issuer update.";
            case "DEF 14A" -> "Governance or shareholder voting materials.";
            case "S-1", "S-1/A" -> "Capital markets or registration filing.";
            default -> "Recent SEC filing.";
        };
    }

    private String buildFilingUrl(String cik, String accessionNumber, String primaryDocument) {
        if (accessionNumber == null || accessionNumber.isBlank() || primaryDocument == null || primaryDocument.isBlank()) {
            return null;
        }

        long cikValue = Long.parseLong(cik);
        String normalizedAccession = accessionNumber.replace("-", "");
        return "https://www.sec.gov/Archives/edgar/data/%d/%s/%s"
                .formatted(cikValue, normalizedAccession, primaryDocument);
    }

    private String arrayText(JsonNode node, String fieldName, int index) {
        JsonNode arrayNode = node.get(fieldName);
        if (arrayNode == null || !arrayNode.isArray() || arrayNode.size() <= index) {
            return "";
        }

        JsonNode valueNode = arrayNode.get(index);
        if (valueNode == null || valueNode.isNull() || valueNode.isMissingNode()) {
            return "";
        }

        return valueNode.asText("");
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

    private record SecCompanyReference(String ticker, String companyName, String cik) {
    }

    private record SecFiling(
            LocalDate filedAt,
            String form,
            String title,
            String summary,
            String url
    ) {
    }
}
