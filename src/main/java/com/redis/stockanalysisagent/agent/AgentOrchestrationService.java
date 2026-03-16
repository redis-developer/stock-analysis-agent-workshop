package com.redis.stockanalysisagent.agent;

import com.redis.stockanalysisagent.agent.coordinatoragent.CoordinatorAgent;
import com.redis.stockanalysisagent.agent.coordinatoragent.ExecutionPlan;
import com.redis.stockanalysisagent.agent.coordinatoragent.RoutingDecision;
import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsAgent;
import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsResult;
import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsSnapshot;
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
    private final FundamentalsAgent fundamentalsAgent;
    private final SynthesisAgent synthesisAgent;

    public AgentOrchestrationService(
            CoordinatorAgent coordinatorAgent,
            MarketDataAgent marketDataAgent,
            FundamentalsAgent fundamentalsAgent,
            SynthesisAgent synthesisAgent
    ) {
        this.coordinatorAgent = coordinatorAgent;
        this.marketDataAgent = marketDataAgent;
        this.fundamentalsAgent = fundamentalsAgent;
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
                    null,
                    resolveCoordinatorMessage(routingDecision),
                    List.of("Coordinator could not produce an execution plan.")
            );
        }

        ExecutionPlan executionPlan = coordinatorAgent.createPlan(routingDecision);
        List<AgentExecution> agentExecutions = new ArrayList<>();
        List<String> limitations = new ArrayList<>();

        MarketSnapshot marketSnapshot = null;
        FundamentalsSnapshot fundamentalsSnapshot = null;
        if (executionPlan.selectedAgents().contains(AgentType.MARKET_DATA)) {
            MarketDataResult marketDataResult = marketDataAgent.execute(request.ticker());
            marketSnapshot = marketDataResult.getFinalResponse();
            agentExecutions.add(new AgentExecution(
                    AgentType.MARKET_DATA,
                    AgentExecutionStatus.COMPLETED,
                    "Market Data Agent fetched a snapshot from the configured provider."
            ));
        }

        if (executionPlan.selectedAgents().contains(AgentType.FUNDAMENTALS)) {
            FundamentalsResult fundamentalsResult = marketSnapshot != null
                    ? fundamentalsAgent.execute(request.ticker(), marketSnapshot)
                    : fundamentalsAgent.execute(request.ticker());
            fundamentalsSnapshot = fundamentalsResult.getFinalResponse();
            agentExecutions.add(new AgentExecution(
                    AgentType.FUNDAMENTALS,
                    AgentExecutionStatus.COMPLETED,
                    "Fundamentals Agent analyzed SEC company facts for the requested ticker."
            ));
        }

        for (AgentType agentType : executionPlan.selectedAgents()) {
            if (agentType == AgentType.MARKET_DATA
                    || agentType == AgentType.FUNDAMENTALS
                    || agentType == AgentType.SYNTHESIS) {
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
                : shouldUseDirectFundamentalsAnswer(executionPlan, fundamentalsSnapshot)
                ? fundamentalsAgent.createDirectAnswer(fundamentalsSnapshot)
                : synthesisAgent.synthesize(request, executionPlan, marketSnapshot, fundamentalsSnapshot, agentExecutions);

        if (executionPlan.requiresSynthesis()) {
            agentExecutions.add(new AgentExecution(
                    AgentType.SYNTHESIS,
                    AgentExecutionStatus.COMPLETED,
                    "Synthesis Agent combined the available agent outputs into the final response."
            ));
        }

        return new AnalysisResponse(
                request.ticker().toUpperCase(),
                request.question(),
                OffsetDateTime.now(),
                executionPlan,
                List.copyOf(agentExecutions),
                marketSnapshot,
                fundamentalsSnapshot,
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

    private boolean shouldUseDirectFundamentalsAnswer(
            ExecutionPlan executionPlan,
            FundamentalsSnapshot fundamentalsSnapshot
    ) {
        return fundamentalsSnapshot != null
                && !executionPlan.requiresSynthesis()
                && executionPlan.selectedAgents().size() == 1
                && executionPlan.selectedAgents().contains(AgentType.FUNDAMENTALS);
    }
}
