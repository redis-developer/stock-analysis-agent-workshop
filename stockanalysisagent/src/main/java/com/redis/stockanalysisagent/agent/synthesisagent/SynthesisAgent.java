package com.redis.stockanalysisagent.agent.synthesisagent;

import com.redis.stockanalysisagent.agent.coordinatoragent.ExecutionPlan;
import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsSnapshot;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import com.redis.stockanalysisagent.agent.newsagent.NewsItem;
import com.redis.stockanalysisagent.agent.newsagent.NewsSnapshot;
import com.redis.stockanalysisagent.agent.orchestration.AgentExecution;
import com.redis.stockanalysisagent.agent.orchestration.AgentExecutionStatus;
import com.redis.stockanalysisagent.agent.orchestration.AgentType;
import com.redis.stockanalysisagent.agent.orchestration.AnalysisRequest;
import com.redis.stockanalysisagent.agent.orchestration.TokenUsageSummary;
import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisSnapshot;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class SynthesisAgent {

    private final ChatClient synthesisChatClient;

    public SynthesisAgent(@Qualifier("synthesisChatClient") ChatClient synthesisChatClient) {
        this.synthesisChatClient = synthesisChatClient;
    }

    public SynthesisResult synthesize(
            AnalysisRequest request,
            ExecutionPlan executionPlan,
            MarketSnapshot marketSnapshot,
            FundamentalsSnapshot fundamentalsSnapshot,
            NewsSnapshot newsSnapshot,
            TechnicalAnalysisSnapshot technicalAnalysisSnapshot,
            List<AgentExecution> agentExecutions
    ) {
        long pendingAgents = agentExecutions.stream()
                .filter(execution -> execution.status() != AgentExecutionStatus.COMPLETED)
                .count();

        ResponseEntity<ChatResponse, SynthesisResponse> response = synthesisChatClient
                .prompt()
                .user(buildPrompt(request, executionPlan, marketSnapshot, fundamentalsSnapshot, newsSnapshot, technicalAnalysisSnapshot))
                .call()
                .responseEntity(SynthesisResponse.class);

        SynthesisResponse entity = response.entity();
        if (entity == null || entity.finalAnswer() == null || entity.finalAnswer().isBlank()) {
            throw new IllegalStateException("Synthesis Agent returned an invalid response.");
        }

        return new SynthesisResult(
                appendPendingContext(entity.finalAnswer().trim(), agentExecutions, pendingAgents),
                TokenUsageSummary.from(response.response())
        );
    }

    private String buildPrompt(
            AnalysisRequest request,
            ExecutionPlan executionPlan,
            MarketSnapshot marketSnapshot,
            FundamentalsSnapshot fundamentalsSnapshot,
            NewsSnapshot newsSnapshot,
            TechnicalAnalysisSnapshot technicalAnalysisSnapshot
    ) {
        return """
                QUESTION
                %s

                REQUESTED_TICKER
                %s

                ROUTING_CONTEXT
                Selected agents: %s
                Routing reasoning: %s

                MARKET_DATA
                %s

                FUNDAMENTALS
                %s

                NEWS
                %s

                TECHNICAL_ANALYSIS
                %s

                INSTRUCTIONS
                - Answer the user directly.
                - Weigh the available signals and call out conflicts or uncertainty.
                - Keep the response grounded in these inputs only.
                """.formatted(
                request.question(),
                request.ticker(),
                executionPlan.selectedAgents(),
                executionPlan.routingReasoning(),
                formatMarketSection(marketSnapshot),
                formatFundamentalsSection(fundamentalsSnapshot),
                formatNewsSection(newsSnapshot),
                formatTechnicalSection(technicalAnalysisSnapshot)
        );
    }

    private String appendPendingContext(String baseAnswer, List<AgentExecution> agentExecutions, long pendingAgents) {
        if (pendingAgents == 0) {
            return baseAnswer;
        }

        return baseAnswer + " Additional context from %s is planned but not implemented in this slice yet."
                .formatted(
                        agentExecutions.stream()
                                .filter(execution -> execution.status() != AgentExecutionStatus.COMPLETED)
                                .map(AgentExecution::agentType)
                                .filter(agentType -> agentType != AgentType.SYNTHESIS)
                                .toList()
                );
    }

    private String formatMarketSection(MarketSnapshot marketSnapshot) {
        if (marketSnapshot == null) {
            return "No market data available.";
        }

        return """
                symbol: %s
                currentPrice: %s
                previousClose: %s
                percentChange: %s
                asOf: %s
                source: %s
                """.formatted(
                marketSnapshot.symbol(),
                marketSnapshot.currentPrice(),
                marketSnapshot.previousClose(),
                marketSnapshot.percentChange(),
                marketSnapshot.asOf(),
                marketSnapshot.source()
        ).trim();
    }

    private String formatFundamentalsSection(FundamentalsSnapshot fundamentalsSnapshot) {
        if (fundamentalsSnapshot == null) {
            return "No fundamentals data available.";
        }

        return """
                companyName: %s
                revenue: %s
                revenueGrowthPercent: %s
                netIncome: %s
                operatingMarginPercent: %s
                netMarginPercent: %s
                priceToSales: %s
                priceToEarnings: %s
                source: %s
                """.formatted(
                fundamentalsSnapshot.companyName(),
                fundamentalsSnapshot.revenue(),
                fundamentalsSnapshot.revenueGrowthPercent(),
                fundamentalsSnapshot.netIncome(),
                fundamentalsSnapshot.operatingMarginPercent(),
                fundamentalsSnapshot.netMarginPercent(),
                fundamentalsSnapshot.priceToSales(),
                fundamentalsSnapshot.priceToEarnings(),
                fundamentalsSnapshot.source()
        ).trim();
    }

    private String formatNewsSection(NewsSnapshot newsSnapshot) {
        if (newsSnapshot == null || (!hasNews(newsSnapshot) && (newsSnapshot.webSummary() == null || newsSnapshot.webSummary().isBlank()))) {
            return "No news data available.";
        }

        StringBuilder builder = new StringBuilder();
        if (newsSnapshot.webSummary() != null && !newsSnapshot.webSummary().isBlank()) {
            builder.append("webSummary: ").append(newsSnapshot.webSummary()).append(System.lineSeparator());
        }
        if (!newsSnapshot.officialItems().isEmpty()) {
            builder.append("officialSignals: ")
                    .append(formatNewsItems(newsSnapshot.officialItems()))
                    .append(System.lineSeparator());
        }
        if (!newsSnapshot.webItems().isEmpty()) {
            builder.append("webNews: ")
                    .append(formatNewsItems(newsSnapshot.webItems()))
                    .append(System.lineSeparator());
        }
        builder.append("source: ").append(newsSnapshot.source());
        return builder.toString().trim();
    }

    private String formatTechnicalSection(TechnicalAnalysisSnapshot technicalAnalysisSnapshot) {
        if (technicalAnalysisSnapshot == null) {
            return "No technical-analysis data available.";
        }

        return """
                interval: %s
                latestClose: %s
                sma20: %s
                ema20: %s
                rsi14: %s
                trendSignal: %s
                momentumSignal: %s
                source: %s
                """.formatted(
                technicalAnalysisSnapshot.interval(),
                technicalAnalysisSnapshot.latestClose(),
                technicalAnalysisSnapshot.sma20(),
                technicalAnalysisSnapshot.ema20(),
                technicalAnalysisSnapshot.rsi14(),
                technicalAnalysisSnapshot.trendSignal(),
                technicalAnalysisSnapshot.momentumSignal(),
                technicalAnalysisSnapshot.source()
        ).trim();
    }

    private String formatNewsItems(List<NewsItem> items) {
        return items.stream()
                .limit(3)
                .map(this::formatNewsItem)
                .toList()
                .toString();
    }

    private String formatNewsItem(NewsItem item) {
        return "{publisher=%s, label=%s, date=%s, title=%s}".formatted(
                item.publisher(),
                item.label(),
                formatLocalDate(item.publishedAt()),
                item.title()
        );
    }

    private String formatLocalDate(LocalDate value) {
        return value != null ? value.toString() : "unavailable";
    }

    private boolean hasNews(NewsSnapshot newsSnapshot) {
        return !newsSnapshot.webItems().isEmpty() || !newsSnapshot.officialItems().isEmpty();
    }
}
