package com.redis.stockanalysisagent.evaluation;

import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsSnapshot;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import com.redis.stockanalysisagent.agent.newsagent.NewsItem;
import com.redis.stockanalysisagent.agent.newsagent.NewsSnapshot;
import com.redis.stockanalysisagent.agent.orchestration.AnalysisRequest;
import com.redis.stockanalysisagent.agent.synthesisagent.SynthesisAgent;
import com.redis.stockanalysisagent.agent.synthesisagent.SynthesisAgentConfig;
import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("llm")
@SpringBootTest(
        classes = SynthesisAgentEvaluationTests.TestApplication.class,
        properties = {
                "spring.main.web-application-type=none",
                "spring.ai.openai.api-key=${OPENAI_API_KEY:${SPRING_AI_OPENAI_API_KEY:test-key}}",
                "spring.ai.openai.chat.options.model=gpt-4o-mini",
                "management.tracing.enabled=false",
                "spring.ai.chat.client.observations.log-prompt=false",
                "spring.ai.chat.client.observations.log-completion=false",
                "spring.ai.chat.observations.log-prompt=false",
                "spring.ai.chat.observations.log-completion=false"
        }
)
class SynthesisAgentEvaluationTests {

    @Autowired
    private SynthesisAgent synthesisAgent;

    @Autowired
    private ChatModel chatModel;

    @BeforeEach
    void requireApiKey() {
        Assumptions.assumeTrue(
                hasApiKey("OPENAI_API_KEY") || hasApiKey("SPRING_AI_OPENAI_API_KEY"),
                "Set OPENAI_API_KEY or SPRING_AI_OPENAI_API_KEY to run LLM judge tests."
        );
    }

    @Test
    void synthesized_answer_is_relevant_to_the_user_question() {
        EvaluationFixture fixture = evaluationFixture();
        // First we ask the real synthesis agent to produce the final answer.
        String answer = synthesize(fixture);

        // Then we ask Spring AI's native evaluator to judge whether that answer
        // actually addresses the original user question.
        EvaluationResponse verdict = new RelevancyEvaluator(ChatClient.builder(chatModel))
                .evaluate(new EvaluationRequest(fixture.request().question(), answer));

        assertThat(verdict.isPass())
                .as(verdict.getFeedback())
                .isTrue();
    }

    @Test
    void synthesized_answer_is_grounded_in_specialist_outputs() {
        EvaluationFixture fixture = evaluationFixture();
        String answer = synthesize(fixture);

        // For groundedness checks, we turn the specialist outputs into Documents
        // and let Spring AI judge whether the synthesized answer stays supported
        // by that evidence.
        EvaluationResponse verdict = FactCheckingEvaluator.builder(ChatClient.builder(chatModel))
                .build()
                .evaluate(new EvaluationRequest(
                        fixture.request().question(),
                        supportingDocuments(fixture),
                        answer
                ));

        assertThat(verdict.isPass())
                .as(verdict.getFeedback())
                .isTrue();
    }

    private String synthesize(EvaluationFixture fixture) {
        // This is a real model call. The evaluators below are real model calls too.
        return synthesisAgent.synthesize(
                fixture.request(),
                fixture.marketSnapshot(),
                fixture.fundamentalsSnapshot(),
                fixture.newsSnapshot(),
                fixture.technicalAnalysisSnapshot()
        ).finalAnswer();
    }

    private List<Document> supportingDocuments(EvaluationFixture fixture) {
        // The fact-checking evaluator works best when we give it a compact evidence
        // set instead of the raw Java objects.
        return List.of(
                new Document("""
                        Market data for %s:
                        current price %s, previous close %s, percent change %s, as of %s.
                        """.formatted(
                        fixture.marketSnapshot().symbol(),
                        fixture.marketSnapshot().currentPrice(),
                        fixture.marketSnapshot().previousClose(),
                        fixture.marketSnapshot().percentChange(),
                        fixture.marketSnapshot().asOf()
                ).trim()),
                new Document("""
                        Fundamentals for %s:
                        revenue %s, revenue growth %s, net income %s, operating margin %s, net margin %s,
                        price to sales %s, price to earnings %s.
                        """.formatted(
                        fixture.fundamentalsSnapshot().companyName(),
                        fixture.fundamentalsSnapshot().revenue(),
                        fixture.fundamentalsSnapshot().revenueGrowthPercent(),
                        fixture.fundamentalsSnapshot().netIncome(),
                        fixture.fundamentalsSnapshot().operatingMarginPercent(),
                        fixture.fundamentalsSnapshot().netMarginPercent(),
                        fixture.fundamentalsSnapshot().priceToSales(),
                        fixture.fundamentalsSnapshot().priceToEarnings()
                ).trim()),
                new Document("""
                        News for %s:
                        official filings %s, web coverage %s, summary %s.
                        """.formatted(
                        fixture.newsSnapshot().companyName(),
                        fixture.newsSnapshot().officialItems(),
                        fixture.newsSnapshot().webItems(),
                        fixture.newsSnapshot().webSummary()
                ).trim()),
                new Document("""
                        Technical analysis for %s:
                        latest close %s, SMA20 %s, EMA20 %s, RSI14 %s, trend %s, momentum %s.
                        """.formatted(
                        fixture.technicalAnalysisSnapshot().ticker(),
                        fixture.technicalAnalysisSnapshot().latestClose(),
                        fixture.technicalAnalysisSnapshot().sma20(),
                        fixture.technicalAnalysisSnapshot().ema20(),
                        fixture.technicalAnalysisSnapshot().rsi14(),
                        fixture.technicalAnalysisSnapshot().trendSignal(),
                        fixture.technicalAnalysisSnapshot().momentumSignal()
                ).trim())
        );
    }

    private EvaluationFixture evaluationFixture() {
        return new EvaluationFixture(
                new AnalysisRequest(
                        "AAPL",
                        "What do the latest signals suggest about AAPL right now?",
                        "What do the latest signals suggest about AAPL right now?"
                ),
                new MarketSnapshot(
                        "AAPL",
                        new BigDecimal("195.40"),
                        new BigDecimal("191.10"),
                        new BigDecimal("4.30"),
                        new BigDecimal("2.25"),
                        OffsetDateTime.parse("2026-03-17T20:00:00Z"),
                        "twelve-data"
                ),
                new FundamentalsSnapshot(
                        "AAPL",
                        "Apple Inc.",
                        "0000320193",
                        new BigDecimal("401000000000"),
                        new BigDecimal("370000000000"),
                        new BigDecimal("8.38"),
                        new BigDecimal("98000000000"),
                        new BigDecimal("123000000000"),
                        new BigDecimal("30.67"),
                        new BigDecimal("24.44"),
                        new BigDecimal("62000000000"),
                        new BigDecimal("98000000000"),
                        new BigDecimal("15500000000"),
                        new BigDecimal("195.40"),
                        new BigDecimal("3028700000000"),
                        new BigDecimal("7.55"),
                        new BigDecimal("6.32"),
                        new BigDecimal("30.92"),
                        LocalDate.of(2025, 9, 27),
                        LocalDate.of(2026, 1, 31),
                        "sec"
                ),
                new NewsSnapshot(
                        "AAPL",
                        "Apple Inc.",
                        List.of(new NewsItem(
                                LocalDate.of(2026, 2, 1),
                                "SEC",
                                "10-Q",
                                "Quarterly report",
                                "Apple reported steady revenue growth and strong margins.",
                                "https://example.com/sec-10q"
                        )),
                        List.of(new NewsItem(
                                LocalDate.of(2026, 3, 16),
                                "Reuters",
                                "Web",
                                "Apple demand remains resilient heading into the next product cycle",
                                "Recent coverage highlights resilient demand and continued investor focus on services growth.",
                                "https://example.com/reuters-apple"
                        )),
                        "Recent coverage points to resilient demand and continued services momentum.",
                        "sec+tavily"
                ),
                new TechnicalAnalysisSnapshot(
                        "AAPL",
                        "1day",
                        OffsetDateTime.parse("2026-03-17T20:00:00Z"),
                        new BigDecimal("195.40"),
                        new BigDecimal("188.10"),
                        new BigDecimal("189.20"),
                        new BigDecimal("61.40"),
                        "Bullish",
                        "Positive",
                        "twelve-data"
                )
        );
    }

    private boolean hasApiKey(String variableName) {
        String value = System.getenv(variableName);
        return value != null && !value.isBlank();
    }

    private record EvaluationFixture(
            AnalysisRequest request,
            MarketSnapshot marketSnapshot,
            FundamentalsSnapshot fundamentalsSnapshot,
            NewsSnapshot newsSnapshot,
            TechnicalAnalysisSnapshot technicalAnalysisSnapshot
    ) {
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({SynthesisAgent.class, SynthesisAgentConfig.class})
    static class TestApplication {
    }
}
