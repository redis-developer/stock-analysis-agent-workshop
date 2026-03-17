package com.redis.stockanalysisagent.api;

import com.redis.stockanalysisagent.agent.orchestration.AgentExecution;
import com.redis.stockanalysisagent.agent.coordinatoragent.ExecutionPlan;
import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsSnapshot;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import com.redis.stockanalysisagent.agent.newsagent.NewsSnapshot;
import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisSnapshot;

import java.time.OffsetDateTime;
import java.util.List;

public record AnalysisResponse(
        String ticker,
        String question,
        OffsetDateTime generatedAt,
        ExecutionPlan executionPlan,
        List<AgentExecution> agentExecutions,
        MarketSnapshot marketSnapshot,
        FundamentalsSnapshot fundamentalsSnapshot,
        NewsSnapshot newsSnapshot,
        TechnicalAnalysisSnapshot technicalAnalysisSnapshot,
        String answer,
        List<String> limitations
) {

    public static AnalysisResponse unableToPlan(
            AnalysisRequest request,
            String answer,
            List<String> limitations
    ) {
        return new AnalysisResponse(
                request.ticker().toUpperCase(),
                request.question(),
                OffsetDateTime.now(),
                null,
                List.of(),
                null,
                null,
                null,
                null,
                answer,
                List.copyOf(limitations)
        );
    }

    public static AnalysisResponse completed(
            AnalysisRequest request,
            ExecutionPlan executionPlan,
            List<AgentExecution> agentExecutions,
            MarketSnapshot marketSnapshot,
            FundamentalsSnapshot fundamentalsSnapshot,
            NewsSnapshot newsSnapshot,
            TechnicalAnalysisSnapshot technicalAnalysisSnapshot,
            String answer,
            List<String> limitations
    ) {
        return new AnalysisResponse(
                request.ticker().toUpperCase(),
                request.question(),
                OffsetDateTime.now(),
                executionPlan,
                List.copyOf(agentExecutions),
                marketSnapshot,
                fundamentalsSnapshot,
                newsSnapshot,
                technicalAnalysisSnapshot,
                answer,
                List.copyOf(limitations)
        );
    }
}
