package com.redis.stockanalysisagent.agent.synthesisagent;

import com.redis.stockanalysisagent.agent.AgentExecution;
import com.redis.stockanalysisagent.agent.AgentExecutionStatus;
import com.redis.stockanalysisagent.agent.AgentType;
import com.redis.stockanalysisagent.agent.coordinatoragent.ExecutionPlan;
import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsSnapshot;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import com.redis.stockanalysisagent.agent.newsagent.NewsItem;
import com.redis.stockanalysisagent.agent.newsagent.NewsSnapshot;
import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisSnapshot;
import com.redis.stockanalysisagent.api.AnalysisRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SynthesisAgentTest {

    @Test
    void fallsBackToDeterministicSynthesisWhenNoChatModelIsConfigured() {
        SynthesisAgent synthesisAgent = new SynthesisAgent(Optional.empty());

        String answer = synthesisAgent.synthesize(
                new AnalysisRequest("AAPL", "Give me a full view on Apple."),
                new ExecutionPlan(
                        List.of(
                                AgentType.MARKET_DATA,
                                AgentType.FUNDAMENTALS,
                                AgentType.NEWS,
                                AgentType.TECHNICAL_ANALYSIS,
                                AgentType.SYNTHESIS
                        ),
                        true,
                        "Broad request."
                ),
                marketSnapshot(),
                fundamentalsSnapshot(),
                newsSnapshot(),
                technicalSnapshot(),
                List.of(
                        new AgentExecution(AgentType.MARKET_DATA, AgentExecutionStatus.COMPLETED, "ok"),
                        new AgentExecution(AgentType.FUNDAMENTALS, AgentExecutionStatus.COMPLETED, "ok"),
                        new AgentExecution(AgentType.NEWS, AgentExecutionStatus.COMPLETED, "ok"),
                        new AgentExecution(AgentType.TECHNICAL_ANALYSIS, AgentExecutionStatus.COMPLETED, "ok")
                )
        );

        assertThat(answer).contains("Apple Inc.");
        assertThat(answer).contains("Recent news signals include");
        assertThat(answer).contains("Technical signals are bullish");
    }

    @Test
    void appendsPendingContextWhenSomePlannedAgentsDidNotComplete() {
        SynthesisAgent synthesisAgent = new SynthesisAgent(Optional.empty());

        String answer = synthesisAgent.synthesize(
                new AnalysisRequest("AAPL", "Give me a full view on Apple."),
                new ExecutionPlan(
                        List.of(AgentType.MARKET_DATA, AgentType.NEWS, AgentType.SYNTHESIS),
                        true,
                        "Multi-signal request."
                ),
                marketSnapshot(),
                null,
                newsSnapshot(),
                null,
                List.of(
                        new AgentExecution(AgentType.MARKET_DATA, AgentExecutionStatus.COMPLETED, "ok"),
                        new AgentExecution(AgentType.NEWS, AgentExecutionStatus.NOT_IMPLEMENTED, "missing")
                )
        );

        assertThat(answer).contains("Additional context from [NEWS] is planned");
    }

    private MarketSnapshot marketSnapshot() {
        return new MarketSnapshot(
                "AAPL",
                new BigDecimal("214.32"),
                new BigDecimal("211.01"),
                new BigDecimal("3.31"),
                new BigDecimal("1.57"),
                OffsetDateTime.parse("2026-03-16T00:00:00Z"),
                "twelve-data"
        );
    }

    private FundamentalsSnapshot fundamentalsSnapshot() {
        return new FundamentalsSnapshot(
                "AAPL",
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
                new BigDecimal("214.32"),
                new BigDecimal("3214800000000.00"),
                new BigDecimal("8.04"),
                new BigDecimal("6.50"),
                new BigDecimal("32.97"),
                LocalDate.parse("2024-09-28"),
                LocalDate.parse("2024-11-01"),
                "sec"
        );
    }

    private NewsSnapshot newsSnapshot() {
        return new NewsSnapshot(
                "AAPL",
                "Apple Inc.",
                List.of(
                        new NewsItem(
                                LocalDate.parse("2026-03-15"),
                                "SEC",
                                "8-K",
                                "Current Report",
                                "8-K items: 2.02,9.01",
                                "https://www.sec.gov/example"
                        )
                ),
                List.of(
                        new NewsItem(
                                null,
                                "reuters.com",
                                "WEB",
                                "Apple shares rise on AI optimism",
                                "Investors focused on Apple's AI roadmap.",
                                "https://www.reuters.com/example"
                        )
                ),
                "Apple faces investor attention around AI strategy.",
                "sec+tavily"
        );
    }

    private TechnicalAnalysisSnapshot technicalSnapshot() {
        return new TechnicalAnalysisSnapshot(
                "AAPL",
                "1day",
                OffsetDateTime.parse("2026-03-16T00:00:00Z"),
                new BigDecimal("120.00"),
                new BigDecimal("110.50"),
                new BigDecimal("110.50"),
                new BigDecimal("100.00"),
                "BULLISH",
                "OVERBOUGHT",
                "twelve-data"
        );
    }
}
