package com.redis.stockanalysisagent.agent;

import com.redis.stockanalysisagent.agent.coordinatoragent.CoordinatorAgent;
import com.redis.stockanalysisagent.agent.coordinatoragent.ExecutionPlan;
import com.redis.stockanalysisagent.agent.coordinatoragent.RoutingDecision;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketDataAgent;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketDataResult;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import com.redis.stockanalysisagent.agent.synthesisagent.SynthesisAgent;
import com.redis.stockanalysisagent.api.AnalysisRequest;
import com.redis.stockanalysisagent.api.AnalysisResponse;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AgentOrchestrationService {

    private final CoordinatorAgent coordinatorAgent;
    private final MarketDataAgent marketDataAgent;
    private final SynthesisAgent synthesisAgent;

    public AgentOrchestrationService(
            CoordinatorAgent coordinatorAgent,
            MarketDataAgent marketDataAgent,
            SynthesisAgent synthesisAgent
    ) {
        this.coordinatorAgent = coordinatorAgent;
        this.marketDataAgent = marketDataAgent;
        this.synthesisAgent = synthesisAgent;
    }

    public AnalysisResponse processRequest(AnalysisRequest request) {
        RoutingDecision routingDecision = coordinatorAgent.execute(request);
        return processRequest(request, routingDecision);
    }

    public AnalysisResponse processRequest(AnalysisRequest request, RoutingDecision routingDecision) {
        if (routingDecision.getFinishReason() != RoutingDecision.FinishReason.COMPLETED) {
            return new AnalysisResponse(
                    request.ticker().toUpperCase(),
                    request.question(),
                    OffsetDateTime.now(),
                    null,
                    List.of(),
                    null,
                    resolveCoordinatorMessage(routingDecision),
                    List.of("Coordinator could not produce an execution plan.")
            );
        }

        ExecutionPlan executionPlan = coordinatorAgent.createPlan(routingDecision);
        List<AgentExecution> agentExecutions = new ArrayList<>();
        List<String> limitations = new ArrayList<>();

        MarketSnapshot marketSnapshot = null;
        if (executionPlan.selectedAgents().contains(AgentType.MARKET_DATA)) {
            MarketDataResult marketDataResult = marketDataAgent.execute(request.ticker());
            marketSnapshot = marketDataResult.getFinalResponse();
            agentExecutions.add(new AgentExecution(
                    AgentType.MARKET_DATA,
                    AgentExecutionStatus.COMPLETED,
                    "Market Data Agent fetched a snapshot from the configured provider."
            ));
        }

        for (AgentType agentType : executionPlan.selectedAgents()) {
            if (agentType == AgentType.MARKET_DATA) {
                continue;
            }

            agentExecutions.add(new AgentExecution(
                    agentType,
                    AgentExecutionStatus.NOT_IMPLEMENTED,
                    "This agent is part of the orchestration plan but has not been implemented yet."
            ));
            limitations.add(agentType + " is not implemented yet.");
        }

        String answer = shouldUseDirectMarketAnswer(executionPlan, marketSnapshot)
                ? marketDataAgent.createDirectAnswer(marketSnapshot)
                : synthesisAgent.synthesize(request, executionPlan, marketSnapshot, agentExecutions);

        return new AnalysisResponse(
                request.ticker().toUpperCase(),
                request.question(),
                OffsetDateTime.now(),
                executionPlan,
                List.copyOf(agentExecutions),
                marketSnapshot,
                answer,
                List.copyOf(limitations)
        );
    }

    private String resolveCoordinatorMessage(RoutingDecision routingDecision) {
        if (routingDecision.getFinishReason() == RoutingDecision.FinishReason.NEEDS_MORE_INPUT
                && routingDecision.getNextPrompt() != null
                && !routingDecision.getNextPrompt().isBlank()) {
            return routingDecision.getNextPrompt();
        }

        if (routingDecision.getFinalResponse() != null && !routingDecision.getFinalResponse().isBlank()) {
            return routingDecision.getFinalResponse();
        }

        return "The coordinator could not complete the request.";
    }

    private boolean shouldUseDirectMarketAnswer(ExecutionPlan executionPlan, MarketSnapshot marketSnapshot) {
        return marketSnapshot != null
                && !executionPlan.requiresSynthesis()
                && executionPlan.selectedAgents().size() == 1
                && executionPlan.selectedAgents().contains(AgentType.MARKET_DATA);
    }
}
