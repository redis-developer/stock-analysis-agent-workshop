package com.redis.stockanalysisagent.agent;

import com.redis.stockanalysisagent.agent.coordinatoragent.CoordinatorAgent;
import com.redis.stockanalysisagent.agent.coordinatoragent.CoordinatorRoutingAgent;
import com.redis.stockanalysisagent.agent.coordinatoragent.RoutingDecision;
import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsAgent;
import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsSnapshot;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketDataAgent;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketDataTools;
import com.redis.stockanalysisagent.agent.newsagent.NewsAgent;
import com.redis.stockanalysisagent.agent.newsagent.NewsItem;
import com.redis.stockanalysisagent.agent.newsagent.NewsSnapshot;
import com.redis.stockanalysisagent.agent.newsagent.NewsTools;
import com.redis.stockanalysisagent.agent.synthesisagent.SynthesisAgent;
import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisAgent;
import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisSnapshot;
import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisTools;
import com.redis.stockanalysisagent.api.AnalysisRequest;
import com.redis.stockanalysisagent.api.AnalysisResponse;
import com.redis.stockanalysisagent.news.tavily.TavilyNewsProvider;
import com.redis.stockanalysisagent.news.tavily.TavilyNewsSearchResult;
import com.redis.stockanalysisagent.news.tavily.TavilyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AgentOrchestrationServiceTest {

    @Test
    void runsIndependentAgentsInParallelAndStillPassesMarketContextToFundamentals() throws Exception {
        CountDownLatch marketStarted = new CountDownLatch(1);
        CountDownLatch newsStarted = new CountDownLatch(1);
        CountDownLatch technicalStarted = new CountDownLatch(1);
        CountDownLatch releaseAgents = new CountDownLatch(1);
        AtomicReference<BigDecimal> fundamentalsObservedPrice = new AtomicReference<>();

        ThreadPoolTaskExecutor executor = taskExecutor();
        try {
            AgentOrchestrationService service = new AgentOrchestrationService(
                    new CoordinatorAgent(new CoordinatorRoutingAgent(Optional.empty())),
                    new MarketDataAgent(
                            ticker -> {
                                marketStarted.countDown();
                                await(releaseAgents);
                                return new MarketSnapshot(
                                        ticker.toUpperCase(),
                                        new BigDecimal("150.00"),
                                        new BigDecimal("148.00"),
                                        new BigDecimal("2.00"),
                                        new BigDecimal("1.35"),
                                        OffsetDateTime.parse("2026-03-16T00:00:00Z"),
                                        "test-market"
                                );
                            },
                            new MarketDataTools(ticker -> {
                                marketStarted.countDown();
                                await(releaseAgents);
                                return new MarketSnapshot(
                                        ticker.toUpperCase(),
                                        new BigDecimal("150.00"),
                                        new BigDecimal("148.00"),
                                        new BigDecimal("2.00"),
                                        new BigDecimal("1.35"),
                                        OffsetDateTime.parse("2026-03-16T00:00:00Z"),
                                        "test-market"
                                );
                            }),
                            Optional.empty()
                    ),
                    new FundamentalsAgent(
                            (ticker, marketSnapshot) -> {
                                fundamentalsObservedPrice.set(marketSnapshot.map(MarketSnapshot::currentPrice).orElse(null));
                                return new FundamentalsSnapshot(
                                        ticker.toUpperCase(),
                                        "Apple Inc.",
                                        "0000320193",
                                        new BigDecimal("400000000000.00"),
                                        new BigDecimal("380000000000.00"),
                                        new BigDecimal("5.26"),
                                        new BigDecimal("100000000000.00"),
                                        new BigDecimal("120000000000.00"),
                                        new BigDecimal("30.00"),
                                        new BigDecimal("25.00"),
                                        new BigDecimal("30000000000.00"),
                                        new BigDecimal("90000000000.00"),
                                        new BigDecimal("15000000000.00"),
                                        fundamentalsObservedPrice.get(),
                                        new BigDecimal("2250000000000.00"),
                                        new BigDecimal("7.50"),
                                        new BigDecimal("6.50"),
                                        new BigDecimal("23.08"),
                                        LocalDate.parse("2025-09-27"),
                                        LocalDate.parse("2025-11-01"),
                                        "test-sec"
                                );
                            },
                            Optional.empty()
                    ),
                    new NewsAgent(
                            new NewsTools(
                                    ticker -> {
                                        newsStarted.countDown();
                                        await(releaseAgents);
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
                                    tavilyStub()
                            ),
                            Optional.empty()
                    ),
                    new TechnicalAnalysisAgent(
                            ticker -> {
                                technicalStarted.countDown();
                                await(releaseAgents);
                                return new TechnicalAnalysisSnapshot(
                                        ticker.toUpperCase(),
                                        "1day",
                                        OffsetDateTime.parse("2026-03-16T00:00:00Z"),
                                        new BigDecimal("151.00"),
                                        new BigDecimal("149.00"),
                                        new BigDecimal("149.50"),
                                        new BigDecimal("58.00"),
                                        "BULLISH",
                                        "NEUTRAL",
                                        "test-technical"
                                );
                            },
                            new TechnicalAnalysisTools(ticker -> {
                                technicalStarted.countDown();
                                await(releaseAgents);
                                return new TechnicalAnalysisSnapshot(
                                        ticker.toUpperCase(),
                                        "1day",
                                        OffsetDateTime.parse("2026-03-16T00:00:00Z"),
                                        new BigDecimal("151.00"),
                                        new BigDecimal("149.00"),
                                        new BigDecimal("149.50"),
                                        new BigDecimal("58.00"),
                                        "BULLISH",
                                        "NEUTRAL",
                                        "test-technical"
                                );
                            }),
                            Optional.empty()
                    ),
                    new SynthesisAgent(Optional.empty()),
                    executor
            );

            AnalysisRequest request = new AnalysisRequest(
                    "AAPL",
                    "Give me a full view on Apple with fundamentals, news, and technical analysis."
            );
            RoutingDecision routingDecision = RoutingDecision.completed(
                    "AAPL",
                    request.question(),
                    List.of(
                            AgentType.FUNDAMENTALS,
                            AgentType.MARKET_DATA,
                            AgentType.NEWS,
                            AgentType.TECHNICAL_ANALYSIS
                    ),
                    true,
                    "Broad stock analysis request."
            );

            CompletableFuture<AnalysisResponse> responseFuture = CompletableFuture.supplyAsync(
                    () -> service.processRequest(request, routingDecision)
            );

            assertThat(marketStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(newsStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(technicalStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(responseFuture.isDone()).isFalse();

            releaseAgents.countDown();

            AnalysisResponse response = responseFuture.get(5, TimeUnit.SECONDS);

            assertThat(fundamentalsObservedPrice.get()).isEqualByComparingTo("150.00");
            assertThat(response.marketSnapshot()).isNotNull();
            assertThat(response.marketSnapshot().source()).isEqualTo("test-market");
            assertThat(response.fundamentalsSnapshot()).isNotNull();
            assertThat(response.fundamentalsSnapshot().currentPrice()).isEqualByComparingTo("150.00");
            assertThat(response.newsSnapshot()).isNotNull();
            assertThat(response.technicalAnalysisSnapshot()).isNotNull();
            assertThat(response.agentExecutions())
                    .extracting(AgentExecution::agentType)
                    .contains(
                            AgentType.FUNDAMENTALS,
                            AgentType.MARKET_DATA,
                            AgentType.NEWS,
                            AgentType.TECHNICAL_ANALYSIS,
                            AgentType.SYNTHESIS
                    );
        } finally {
            releaseAgents.countDown();
            executor.shutdown();
        }
    }

    private static ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("test-agent-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(8);
        executor.initialize();
        return executor;
    }

    private static TavilyNewsProvider tavilyStub() {
        return new TavilyNewsProvider(
                RestClient.builder(),
                new TavilyProperties(),
                new com.redis.stockanalysisagent.cache.ExternalDataCache(new ConcurrentMapCacheManager())
        ) {
            @Override
            public TavilyNewsSearchResult search(String ticker, String companyName, String question) {
                return TavilyNewsSearchResult.empty();
            }
        };
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while waiting for the parallel orchestration test latch.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the parallel orchestration test latch.", ex);
        }
    }
}
