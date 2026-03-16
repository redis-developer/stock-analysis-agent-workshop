package com.redis.stockanalysisagent.news.tavily;

import com.redis.stockanalysisagent.agent.newsagent.NewsItem;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TavilyNewsProvider {

    private final RestClient restClient;
    private final TavilyProperties properties;

    public TavilyNewsProvider(RestClient.Builder restClientBuilder, TavilyProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl().toString())
                .build();
    }

    public TavilyNewsSearchResult search(String ticker, String companyName, String question) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return TavilyNewsSearchResult.empty();
        }

        JsonNode response = restClient.post()
                .uri("/search")
                .body(requestBody(ticker, companyName, question))
                .header("Authorization", "Bearer " + properties.getApiKey())
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            return TavilyNewsSearchResult.empty();
        }

        if ("error".equalsIgnoreCase(optionalText(response, "status", ""))) {
            throw new IllegalStateException("Tavily error: " + optionalText(response, "error", "Unknown error"));
        }

        List<NewsItem> items = response.path("results").isArray()
                ? response.path("results").valueStream()
                .limit(properties.getMaxResults())
                .map(this::toNewsItem)
                .toList()
                : List.of();

        return new TavilyNewsSearchResult(items, optionalText(response, "answer", null));
    }

    private Map<String, Object> requestBody(String ticker, String companyName, String question) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", searchQuery(ticker, companyName, question));
        body.put("topic", "finance");
        body.put("search_depth", "basic");
        body.put("max_results", properties.getMaxResults());
        body.put("include_answer", "basic");
        body.put("include_raw_content", false);
        return body;
    }

    private String searchQuery(String ticker, String companyName, String question) {
        String companyPart = companyName != null && !companyName.isBlank()
                ? companyName
                : ticker.toUpperCase();
        return "%s (%s) investor news: %s".formatted(companyPart, ticker.toUpperCase(), question);
    }

    private NewsItem toNewsItem(JsonNode node) {
        String url = optionalText(node, "url", null);
        return new NewsItem(
                null,
                publisherFrom(url),
                "WEB",
                optionalText(node, "title", "Untitled result"),
                optionalText(node, "content", ""),
                url
        );
    }

    private String publisherFrom(String url) {
        if (url == null || url.isBlank()) {
            return "Web";
        }

        try {
            String host = URI.create(url).getHost();
            if (host == null || host.isBlank()) {
                return "Web";
            }
            return host.replaceFirst("^www\\.", "");
        } catch (RuntimeException ignored) {
            return "Web";
        }
    }

    private String optionalText(JsonNode node, String fieldName, String defaultValue) {
        JsonNode field = node.get(fieldName);
        if (field == null || field.isNull() || field.isMissingNode()) {
            return defaultValue;
        }
        return field.asText(defaultValue);
    }
}
