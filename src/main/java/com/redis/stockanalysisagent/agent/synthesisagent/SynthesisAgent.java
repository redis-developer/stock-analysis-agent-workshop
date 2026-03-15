package com.redis.stockanalysisagent.agent.synthesisagent;

import com.redis.stockanalysisagent.agent.AgentExecution;
import com.redis.stockanalysisagent.agent.AgentExecutionStatus;
import com.redis.stockanalysisagent.agent.AgentType;
import com.redis.stockanalysisagent.agent.coordinatoragent.ExecutionPlan;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import com.redis.stockanalysisagent.api.AnalysisRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SynthesisAgent {

    public String synthesize(
            AnalysisRequest request,
            ExecutionPlan executionPlan,
            MarketSnapshot marketSnapshot,
            List<AgentExecution> agentExecutions
    ) {
        long pendingAgents = agentExecutions.stream()
                .filter(execution -> execution.status() != AgentExecutionStatus.COMPLETED)
                .count();

        String baseAnswer = marketSnapshot != null
                ? """
                    Based on the currently implemented agents, %s is trading at $%s (%s%% vs. previous close).
                    The coordinator selected %s for the question "%s".
                    """.formatted(
                        marketSnapshot.symbol(),
                        marketSnapshot.currentPrice(),
                        marketSnapshot.percentChange(),
                        executionPlan.selectedAgents(),
                        request.question()
                ).replace('\n', ' ').trim()
                : """
                    The coordinator selected %s for the question "%s".
                    The required specialized analysis is planned, but the selected agents are not implemented in this slice yet.
                    """.formatted(
                        executionPlan.selectedAgents(),
                        request.question()
                ).replace('\n', ' ').trim();

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
}
