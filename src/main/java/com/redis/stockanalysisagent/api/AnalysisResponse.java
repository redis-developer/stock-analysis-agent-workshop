package com.redis.stockanalysisagent.api;

import com.redis.stockanalysisagent.agent.AgentExecution;
import com.redis.stockanalysisagent.agent.coordinatoragent.ExecutionPlan;
import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsSnapshot;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;

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
        String answer,
        List<String> limitations
) {
}
