package com.redis.stockanalysisagent.agent.newsagent;

import com.redis.stockanalysisagent.cache.ExternalDataCache;
import com.redis.stockanalysisagent.news.tavily.TavilyNewsProvider;
import com.redis.stockanalysisagent.news.tavily.TavilyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class NewsAgentTest {

    @Test
    void fallsBackToDeterministicProviderWhenNoChatModelIsConfigured() {
        AtomicInteger providerCalls = new AtomicInteger();
        TavilyProperties tavilyProperties = new TavilyProperties();
        ExternalDataCache externalDataCache = new ExternalDataCache(new ConcurrentMapCacheManager());
        TavilyNewsProvider tavilyNewsProvider = new TavilyNewsProvider(RestClient.builder(), tavilyProperties, externalDataCache);

        NewsAgent agent = new NewsAgent(
                new NewsTools(
                        ticker -> {
                            providerCalls.incrementAndGet();
                            return new NewsSnapshot(
                                    ticker.toUpperCase(),
                                    "Apple Inc.",
                                    List.of(
                                            new NewsItem(
                                                    LocalDate.parse("2026-03-15"),
                                                    "SEC",
                                                    "8-K",
                                                    "Current Report",
                                                    "Company event signal.",
                                                    "https://www.sec.gov/example"
                                            )
                                    ),
                                    List.of(),
                                    null,
                                    "test-sec-news"
                            );
                        },
                        tavilyNewsProvider
                ),
                Optional.empty()
        );

        NewsResult result = agent.execute("AAPL", "What recent news should I know about Apple?");

        assertThat(providerCalls).hasValue(1);
        assertThat(result.getFinishReason()).isEqualTo(NewsResult.FinishReason.COMPLETED);
        assertThat(result.getFinalResponse().ticker()).isEqualTo("AAPL");
        assertThat(result.getMessage()).contains("Recent company-event signals");
    }
}
