package com.redis.stockanalysisagent.api;

import com.redis.stockanalysisagent.agent.AgentExecutionStatus;
import com.redis.stockanalysisagent.agent.AgentType;
import com.redis.stockanalysisagent.agent.coordinatoragent.CoordinatorRoutingAgent;
import com.redis.stockanalysisagent.agent.coordinatoragent.RoutingDecision;
import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsSnapshot;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import com.redis.stockanalysisagent.fundamentals.FundamentalsProvider;
import org.junit.jupiter.api.Test;
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
        assertThat(response.agentExecutions().get(1).status()).isEqualTo(AgentExecutionStatus.COMPLETED);
        assertThat(response.agentExecutions().get(2).status()).isEqualTo(AgentExecutionStatus.NOT_IMPLEMENTED);
        assertThat(response.limitations()).contains("NEWS is not implemented yet.");
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
