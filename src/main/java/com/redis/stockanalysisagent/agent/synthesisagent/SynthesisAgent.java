package com.redis.stockanalysisagent.agent.synthesisagent;

import com.redis.stockanalysisagent.agent.AgentExecution;
import com.redis.stockanalysisagent.agent.AgentExecutionStatus;
import com.redis.stockanalysisagent.agent.AgentType;
import com.redis.stockanalysisagent.agent.coordinatoragent.ExecutionPlan;
import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsSnapshot;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import com.redis.stockanalysisagent.agent.newsagent.NewsSnapshot;
import com.redis.stockanalysisagent.api.AnalysisRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class SynthesisAgent {

    public String synthesize(
            AnalysisRequest request,
            ExecutionPlan executionPlan,
            MarketSnapshot marketSnapshot,
            FundamentalsSnapshot fundamentalsSnapshot,
            NewsSnapshot newsSnapshot,
            List<AgentExecution> agentExecutions
    ) {
        long pendingAgents = agentExecutions.stream()
                .filter(execution -> execution.status() != AgentExecutionStatus.COMPLETED)
                .count();

        String baseAnswer = buildBaseAnswer(request, executionPlan, marketSnapshot, fundamentalsSnapshot, newsSnapshot);

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

    private String buildBaseAnswer(
            AnalysisRequest request,
            ExecutionPlan executionPlan,
            MarketSnapshot marketSnapshot,
            FundamentalsSnapshot fundamentalsSnapshot,
            NewsSnapshot newsSnapshot
    ) {
        if (marketSnapshot != null && fundamentalsSnapshot != null && newsSnapshot != null && hasNews(newsSnapshot)) {
            return """
                    Based on the currently implemented agents, %s is trading at $%s (%s%% vs. previous close).
                    %s reported revenue of %s with revenue growth of %s and net margin of %s.
                    Recent news signals include %s.
                    The coordinator selected %s for the question "%s".
                    """.formatted(
                    marketSnapshot.symbol(),
                    marketSnapshot.currentPrice(),
                    marketSnapshot.percentChange(),
                    fundamentalsSnapshot.companyName(),
                    formatMoney(fundamentalsSnapshot.revenue()),
                    formatPercent(fundamentalsSnapshot.revenueGrowthPercent()),
                    formatPercent(fundamentalsSnapshot.netMarginPercent()),
                    newsHighlight(newsSnapshot),
                    executionPlan.selectedAgents(),
                    request.question()
            ).replace('\n', ' ').trim();
        }

        if (marketSnapshot != null && fundamentalsSnapshot != null) {
            return """
                    Based on the currently implemented agents, %s is trading at $%s (%s%% vs. previous close).
                    %s reported revenue of %s with revenue growth of %s and a net margin of %s.
                    The coordinator selected %s for the question "%s".
                    """.formatted(
                    marketSnapshot.symbol(),
                    marketSnapshot.currentPrice(),
                    marketSnapshot.percentChange(),
                    fundamentalsSnapshot.companyName(),
                    formatMoney(fundamentalsSnapshot.revenue()),
                    formatPercent(fundamentalsSnapshot.revenueGrowthPercent()),
                    formatPercent(fundamentalsSnapshot.netMarginPercent()),
                    executionPlan.selectedAgents(),
                    request.question()
            ).replace('\n', ' ').trim();
        }

        if (marketSnapshot != null) {
            return """
                    Based on the currently implemented agents, %s is trading at $%s (%s%% vs. previous close).
                    The coordinator selected %s for the question "%s".
                    """.formatted(
                    marketSnapshot.symbol(),
                    marketSnapshot.currentPrice(),
                    marketSnapshot.percentChange(),
                    executionPlan.selectedAgents(),
                    request.question()
            ).replace('\n', ' ').trim();
        }

        if (fundamentalsSnapshot != null) {
            return """
                    %s reported revenue of %s and net income of %s.
                    Revenue growth was %s and net margin was %s.
                    The coordinator selected %s for the question "%s".
                    """.formatted(
                    fundamentalsSnapshot.companyName(),
                    formatMoney(fundamentalsSnapshot.revenue()),
                    formatMoney(fundamentalsSnapshot.netIncome()),
                    formatPercent(fundamentalsSnapshot.revenueGrowthPercent()),
                    formatPercent(fundamentalsSnapshot.netMarginPercent()),
                    executionPlan.selectedAgents(),
                    request.question()
            ).replace('\n', ' ').trim();
        }

        if (newsSnapshot != null && hasNews(newsSnapshot)) {
            return """
                    Recent news signals for %s include %s.
                    The coordinator selected %s for the question "%s".
                    """.formatted(
                    newsSnapshot.companyName(),
                    newsHighlight(newsSnapshot),
                    executionPlan.selectedAgents(),
                    request.question()
            ).replace('\n', ' ').trim();
        }

        return """
                The coordinator selected %s for the question "%s".
                The required specialized analysis is planned, but the selected agents are not implemented in this slice yet.
                """.formatted(
                executionPlan.selectedAgents(),
                request.question()
        ).replace('\n', ' ').trim();
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) {
            return "unavailable";
        }

        BigDecimal billions = value.divide(BigDecimal.valueOf(1_000_000_000L), 2, RoundingMode.HALF_UP);
        return "$" + billions + "B";
    }

    private String formatPercent(BigDecimal value) {
        if (value == null) {
            return "unavailable";
        }

        return value.setScale(2, RoundingMode.HALF_UP) + "%";
    }

    private boolean hasNews(NewsSnapshot newsSnapshot) {
        return !newsSnapshot.webItems().isEmpty() || !newsSnapshot.officialItems().isEmpty();
    }

    private String newsHighlight(NewsSnapshot newsSnapshot) {
        if (!newsSnapshot.webItems().isEmpty()) {
            String firstTitle = newsSnapshot.webItems().getFirst().title();
            String firstPublisher = newsSnapshot.webItems().getFirst().publisher();
            if (!newsSnapshot.officialItems().isEmpty()) {
                return "%s (%s), alongside official SEC signals such as %s on %s".formatted(
                        firstTitle,
                        firstPublisher,
                        newsSnapshot.officialItems().getFirst().label(),
                        newsSnapshot.officialItems().getFirst().publishedAt()
                );
            }

            return "%s (%s)".formatted(firstTitle, firstPublisher);
        }

        return "%s on %s".formatted(
                newsSnapshot.officialItems().getFirst().label(),
                newsSnapshot.officialItems().getFirst().publishedAt()
        );
    }
}
