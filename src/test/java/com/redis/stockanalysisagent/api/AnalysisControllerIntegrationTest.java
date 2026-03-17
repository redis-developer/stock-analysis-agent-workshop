package com.redis.stockanalysisagent.api;

import com.redis.stockanalysisagent.agent.AgentExecutionStatus;
import com.redis.stockanalysisagent.agent.AgentType;
import com.redis.stockanalysisagent.agent.coordinatoragent.CoordinatorRoutingAgent;
import com.redis.stockanalysisagent.agent.coordinatoragent.RoutingDecision;
import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsSnapshot;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import com.redis.stockanalysisagent.agent.newsagent.NewsItem;
import com.redis.stockanalysisagent.agent.newsagent.NewsSnapshot;
import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisSnapshot;
import com.redis.stockanalysisagent.fundamentals.FundamentalsProvider;
import com.redis.stockanalysisagent.news.NewsProvider;
import com.redis.stockanalysisagent.news.tavily.TavilyNewsProvider;
import com.redis.stockanalysisagent.news.tavily.TavilyNewsSearchResult;
import com.redis.stockanalysisagent.technicalanalysis.TechnicalAnalysisProvider;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AnalysisControllerIntegrationTest.TestRoutingConfiguration.class)
class AnalysisControllerIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Test
    void returnsDirectMarketAnswerForSimplePriceQuestion() throws Exception {
        AnalysisResponse response = post(new AnalysisRequest("AAPL", "What is the stock price right now?"));

        assertThat(response).isNotNull();
        assertThat(response.ticker()).isEqualTo("AAPL");
        assertThat(response.executionPlan().requiresSynthesis()).isFalse();
        assertThat(response.executionPlan().selectedAgents()).containsExactly(AgentType.MARKET_DATA);
        assertThat(response.executionPlan().routingReasoning()).isEqualTo("Simple price lookup.");
        assertThat(response.marketSnapshot().source()).isEqualTo("mock");
        assertThat(response.answer()).isNotBlank();
    }

    @Test
    void returnsPlannedButUnimplementedAgentsForBroaderQuestions() throws Exception {
        AnalysisResponse response = post(new AnalysisRequest(
                "NVDA",
                "Give me a full view with fundamentals, news, and technical analysis."
        ));

        assertThat(response).isNotNull();
        assertThat(response.executionPlan().requiresSynthesis()).isTrue();
        assertThat(response.executionPlan().selectedAgents()).containsExactly(
                AgentType.MARKET_DATA,
                AgentType.FUNDAMENTALS,
                AgentType.NEWS,
                AgentType.TECHNICAL_ANALYSIS,
                AgentType.SYNTHESIS
        );
        assertThat(response.fundamentalsSnapshot()).isNotNull();
        assertThat(response.fundamentalsSnapshot().source()).isEqualTo("test-sec");
        assertThat(response.newsSnapshot()).isNotNull();
        assertThat(response.newsSnapshot().source()).isEqualTo("test-sec-news+tavily");
        assertThat(response.newsSnapshot().webItems()).hasSize(1);
        assertThat(response.technicalAnalysisSnapshot()).isNotNull();
        assertThat(response.technicalAnalysisSnapshot().source()).isEqualTo("test-twelve-data");
        assertThat(response.agentExecutions()).allSatisfy(execution -> assertThat(execution.durationMs()).isGreaterThanOrEqualTo(0));
        assertThat(response.agentExecutions().get(1).status()).isEqualTo(AgentExecutionStatus.COMPLETED);
        assertThat(response.agentExecutions().get(2).status()).isEqualTo(AgentExecutionStatus.COMPLETED);
        assertThat(response.agentExecutions().get(3).status()).isEqualTo(AgentExecutionStatus.COMPLETED);
        assertThat(response.limitations()).isEmpty();
    }

    @Test
    void continuesWhenOneSelectedAgentFails() {
        AnalysisResponse response = post(new AnalysisRequest(
                "FAILNEWS",
                "Give me a full view with fundamentals, news, and technical analysis."
        ));

        assertThat(response).isNotNull();
        assertThat(response.marketSnapshot()).isNotNull();
        assertThat(response.fundamentalsSnapshot()).isNotNull();
        assertThat(response.technicalAnalysisSnapshot()).isNotNull();
        assertThat(response.newsSnapshot()).isNull();
        assertThat(response.agentExecutions())
                .anyMatch(execution -> execution.agentType() == AgentType.NEWS
                        && execution.status() == AgentExecutionStatus.FAILED);
        assertThat(response.agentExecutions())
                .anyMatch(execution -> execution.agentType() == AgentType.SYNTHESIS
                        && execution.status() == AgentExecutionStatus.COMPLETED);
        assertThat(response.limitations())
                .anyMatch(limit -> limit.contains("NEWS failed"));
        assertThat(response.answer()).isNotBlank();
    }

    @Test
    void returnsDirectFundamentalsAnswerForFundamentalsOnlyQuestion() {
        AnalysisResponse response = post(new AnalysisRequest(
                "AAPL",
                "How do Apple's fundamentals look?"
        ));

        assertThat(response).isNotNull();
        assertThat(response.executionPlan().requiresSynthesis()).isFalse();
        assertThat(response.executionPlan().selectedAgents()).containsExactly(AgentType.FUNDAMENTALS);
        assertThat(response.fundamentalsSnapshot()).isNotNull();
        assertThat(response.marketSnapshot()).isNull();
        assertThat(response.answer()).contains("Apple Inc.");
    }

    @Test
    void returnsDirectNewsAnswerForNewsOnlyQuestion() {
        AnalysisResponse response = post(new AnalysisRequest(
                "AAPL",
                "What recent news should I know about Apple?"
        ));

        assertThat(response).isNotNull();
        assertThat(response.executionPlan().requiresSynthesis()).isFalse();
        assertThat(response.executionPlan().selectedAgents()).containsExactly(AgentType.NEWS);
        assertThat(response.newsSnapshot()).isNotNull();
        assertThat(response.answer()).contains("Recent web coverage");
        assertThat(response.limitations()).isEmpty();
    }

    @Test
    void returnsDirectTechnicalAnswerForTechnicalOnlyQuestion() {
        AnalysisResponse response = post(new AnalysisRequest(
                "AAPL",
                "What do the technicals look like for Apple?"
        ));

        assertThat(response).isNotNull();
        assertThat(response.executionPlan().requiresSynthesis()).isFalse();
        assertThat(response.executionPlan().selectedAgents()).containsExactly(AgentType.TECHNICAL_ANALYSIS);
        assertThat(response.technicalAnalysisSnapshot()).isNotNull();
        assertThat(response.answer()).contains("Technical signals");
        assertThat(response.limitations()).isEmpty();
    }

    private AnalysisResponse post(AnalysisRequest request) {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build()
                .post()
                .uri("/analysis")
                .body(request)
                .retrieve()
                .onStatus(status -> status.isError(), (req, res) -> {
                    throw new IllegalStateException("Unexpected HTTP status: " + res.getStatusCode());
                })
                .body(AnalysisResponse.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestRoutingConfiguration {

        @Bean
        @Primary
        CoordinatorRoutingAgent coordinatorRoutingAgent() {
            return new CoordinatorRoutingAgent(Optional.empty()) {
                @Override
                public RoutingDecision route(AnalysisRequest request) {
                    String question = request.question().toLowerCase();
                    if (question.contains("technical") && !question.contains("fundamentals") && !question.contains("news")) {
                        return RoutingDecision.completed(
                                request.ticker(),
                                request.question(),
                                java.util.List.of(AgentType.TECHNICAL_ANALYSIS),
                                false,
                                "Technical-only request."
                        );
                    }

                    if (question.contains("news") && !question.contains("fundamentals") && !question.contains("technical")) {
                        return RoutingDecision.completed(
                                request.ticker(),
                                request.question(),
                                java.util.List.of(AgentType.NEWS),
                                false,
                                "News-only request."
                        );
                    }

                    if (question.contains("fundamentals") || question.contains("news") || question.contains("technical")) {
                        if (!question.contains("news") && !question.contains("technical")) {
                            return RoutingDecision.completed(
                                    request.ticker(),
                                    request.question(),
                                    java.util.List.of(AgentType.FUNDAMENTALS),
                                    false,
                                    "Fundamentals-only request."
                            );
                        }

                        return RoutingDecision.of(
                                java.util.List.of(
                                        AgentType.MARKET_DATA,
                                        AgentType.FUNDAMENTALS,
                                        AgentType.NEWS,
                                        AgentType.TECHNICAL_ANALYSIS
                                ),
                                true,
                                "Broad stock analysis request."
                        );
                    }

                    return RoutingDecision.of(
                            java.util.List.of(AgentType.MARKET_DATA),
                            false,
                            "Simple price lookup."
                    );
                }
            };
        }

        @Bean
        @Primary
        FundamentalsProvider fundamentalsProvider() {
            return (ticker, marketSnapshot) -> snapshot(ticker, marketSnapshot.map(MarketSnapshot::currentPrice).orElse(null));
        }

        @Bean
        @Primary
        TechnicalAnalysisProvider technicalAnalysisProvider() {
            return ticker -> new TechnicalAnalysisSnapshot(
                    ticker.toUpperCase(),
                    "1day",
                    OffsetDateTime.parse("2026-03-16T00:00:00Z"),
                    new BigDecimal("120.00"),
                    new BigDecimal("110.50"),
                    new BigDecimal("110.50"),
                    new BigDecimal("100.00"),
                    "BULLISH",
                    "OVERBOUGHT",
                    "test-twelve-data"
            );
        }

        @Bean
        @Primary
        NewsProvider newsProvider() {
            return ticker -> {
                if ("FAILNEWS".equalsIgnoreCase(ticker)) {
                    throw new IllegalStateException("Simulated news failure for degraded orchestration test.");
                }

                return new NewsSnapshot(
                    ticker.toUpperCase(),
                    "Apple Inc.",
                    java.util.List.of(
                            new NewsItem(
                                    LocalDate.parse("2026-03-15"),
                                    "SEC",
                                    "8-K",
                                    "Current Report",
                                    "8-K items: 2.02,9.01",
                                    "https://www.sec.gov/example"
                            ),
                            new NewsItem(
                                    LocalDate.parse("2026-02-01"),
                                    "SEC",
                                    "10-Q",
                                    "Quarterly Report",
                                    "Quarterly financial filing.",
                                    "https://www.sec.gov/example-10q"
                            )
                    ),
                    java.util.List.of(),
                    null,
                    "test-sec-news"
                );
            };
        }

        @Bean
        @Primary
        TavilyNewsProvider tavilyNewsProvider() {
            return new TavilyNewsProvider(
                    RestClient.builder(),
                    new com.redis.stockanalysisagent.news.tavily.TavilyProperties(),
                    new com.redis.stockanalysisagent.cache.ExternalDataCache(new ConcurrentMapCacheManager())
            ) {
                @Override
                public TavilyNewsSearchResult search(String ticker, String companyName, String question) {
                    return new TavilyNewsSearchResult(
                            java.util.List.of(
                                    new NewsItem(
                                            null,
                                            "reuters.com",
                                            "WEB",
                                            "Apple shares rise on AI optimism",
                                            "Investors focused on Apple's AI roadmap.",
                                            "https://www.reuters.com/example"
                                    )
                            ),
                            "Apple faces investor attention around a 5% move in AI-related sentiment."
                    );
                }
            };
        }

        private FundamentalsSnapshot snapshot(String ticker, BigDecimal currentPrice) {
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
                    currentPrice,
                    currentPrice != null ? currentPrice.multiply(new BigDecimal("15000000000.00")) : null,
                    currentPrice != null ? new BigDecimal("7.50") : null,
                    new BigDecimal("6.50"),
                    currentPrice != null ? new BigDecimal("30.77") : null,
                    LocalDate.parse("2024-09-28"),
                    LocalDate.parse("2024-11-01"),
                    "test-sec"
            );
        }
    }
}
