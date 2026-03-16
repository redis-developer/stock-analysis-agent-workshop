package com.redis.stockanalysisagent.news.tavily;

import com.redis.stockanalysisagent.agent.newsagent.NewsItem;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TavilyNewsProviderTest {

    @Test
    void normalizesTavilySearchResultsIntoWebNewsItems() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        TavilyNewsProvider provider = new TavilyNewsProvider(restClientBuilder, properties("tvly-demo"));

        server.expect(requestTo("https://api.tavily.com/search"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "answer": "Apple faces investor attention around new product demand and AI strategy.",
                          "results": [
                            {
                              "title": "Apple shares rise on AI optimism",
                              "url": "https://www.reuters.com/markets/apple-shares-rise",
                              "content": "Investors focused on Apple's AI roadmap and product cycle.",
                              "score": 0.92
                            },
                            {
                              "title": "Why Apple investors are watching services growth",
                              "url": "https://www.cnbc.com/2026/03/16/apple-services-growth.html",
                              "content": "Services revenue remains a key watchpoint for investors.",
                              "score": 0.88
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        TavilyNewsSearchResult result = provider.search("AAPL", "Apple Inc.", "What recent news matters for Apple investors?");

        assertThat(result.answer()).contains("Apple faces investor attention");
        assertThat(result.items()).hasSize(2);

        NewsItem firstItem = result.items().getFirst();
        assertThat(firstItem.publisher()).isEqualTo("reuters.com");
        assertThat(firstItem.label()).isEqualTo("WEB");
        assertThat(firstItem.title()).isEqualTo("Apple shares rise on AI optimism");
        assertThat(firstItem.summary()).contains("AI roadmap");
        assertThat(firstItem.url()).isEqualTo("https://www.reuters.com/markets/apple-shares-rise");

        server.verify();
    }

    @Test
    void returnsEmptyResultsWhenNoApiKeyIsConfigured() {
        TavilyNewsProvider provider = new TavilyNewsProvider(RestClient.builder(), properties(""));

        TavilyNewsSearchResult result = provider.search("AAPL", "Apple Inc.", "What recent news matters for Apple investors?");

        assertThat(result.items()).isEmpty();
        assertThat(result.answer()).isNull();
    }

    private TavilyProperties properties(String apiKey) {
        TavilyProperties properties = new TavilyProperties();
        properties.setBaseUrl(URI.create("https://api.tavily.com"));
        properties.setApiKey(apiKey);
        properties.setMaxResults(5);
        return properties;
    }
}
