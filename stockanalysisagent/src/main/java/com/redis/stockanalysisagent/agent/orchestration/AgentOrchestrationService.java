package com.redis.stockanalysisagent.agent.orchestration;

import com.redis.stockanalysisagent.agent.coordinatoragent.ExecutionPlan;
import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsAgent;
import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsResult;
import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsSnapshot;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketDataAgent;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketDataResult;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import com.redis.stockanalysisagent.agent.newsagent.NewsAgent;
import com.redis.stockanalysisagent.agent.newsagent.NewsResult;
import com.redis.stockanalysisagent.agent.newsagent.NewsSnapshot;
import com.redis.stockanalysisagent.agent.synthesisagent.SynthesisAgent;
import com.redis.stockanalysisagent.agent.synthesisagent.SynthesisResult;
import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisAgent;
import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisResult;
import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisSnapshot;
import org.springframework.stereotype.Service;

@Service
public class AgentOrchestrationService {

    private final MarketDataAgent marketDataAgent;
    private final FundamentalsAgent fundamentalsAgent;
    private final NewsAgent newsAgent;
    private final TechnicalAnalysisAgent technicalAnalysisAgent;
    private final SynthesisAgent synthesisAgent;

    public AgentOrchestrationService(
            MarketDataAgent marketDataAgent,
            FundamentalsAgent fundamentalsAgent,
            NewsAgent newsAgent,
            TechnicalAnalysisAgent technicalAnalysisAgent,
            SynthesisAgent synthesisAgent
    ) {
        this.marketDataAgent = marketDataAgent;
        this.fundamentalsAgent = fundamentalsAgent;
        this.newsAgent = newsAgent;
        this.technicalAnalysisAgent = technicalAnalysisAgent;
        this.synthesisAgent = synthesisAgent;
    }

    public AnalysisResponse processRequest(AnalysisRequest request, ExecutionPlan executionPlan) {
        AgentExecutionState state = executeSelectedAgents(request, executionPlan);
        String answer = composeAnswer(request, executionPlan, state);

        return AnalysisResponse.completed(state.agentExecutions(), answer);
    }

    private AgentExecutionState executeSelectedAgents(AnalysisRequest request, ExecutionPlan executionPlan) {
        AgentExecutionState state = new AgentExecutionState();

        for (AgentType agentType : executionPlan.selectedAgents()) {
            if (agentType == AgentType.SYNTHESIS) {
                continue;
            }

            switch (agentType) {
                case MARKET_DATA -> executeMarketData(request, state);
                case FUNDAMENTALS -> executeFundamentals(request, state);
                case NEWS -> executeNews(request, state);
                case TECHNICAL_ANALYSIS -> executeTechnicalAnalysis(request, state);
                case SYNTHESIS -> throw new IllegalStateException(
                        "Synthesis should execute only after the specialized agents finish."
                );
            }
        }

        return state;
    }

    private void executeMarketData(AnalysisRequest request, AgentExecutionState state) {
        long startedAt = System.nanoTime();
        MarketDataResult marketDataResult = marketDataAgent.execute(request.ticker(), request.question());

        state.addExecution(new AgentExecution(
                AgentType.MARKET_DATA,
                AgentExecutionStatus.COMPLETED,
                marketDataResult.getMessage(),
                elapsedDurationMs(startedAt),
                marketDataResult.getTokenUsage()
        ));
        state.putStructuredOutput(AgentType.MARKET_DATA, marketDataResult.getFinalResponse());
    }

    private void executeFundamentals(AnalysisRequest request, AgentExecutionState state) {
        long startedAt = System.nanoTime();
        MarketSnapshot marketSnapshot = structuredOutput(state, AgentType.MARKET_DATA, MarketSnapshot.class);
        FundamentalsResult fundamentalsResult = marketSnapshot != null
                ? fundamentalsAgent.execute(request.ticker(), request.question(), marketSnapshot)
                : fundamentalsAgent.execute(request.ticker(), request.question());

        state.addExecution(new AgentExecution(
                AgentType.FUNDAMENTALS,
                AgentExecutionStatus.COMPLETED,
                fundamentalsResult.getMessage(),
                elapsedDurationMs(startedAt),
                fundamentalsResult.getTokenUsage()
        ));
        state.putStructuredOutput(AgentType.FUNDAMENTALS, fundamentalsResult.getFinalResponse());
    }

    private void executeNews(AnalysisRequest request, AgentExecutionState state) {
        long startedAt = System.nanoTime();
        NewsResult newsResult = newsAgent.execute(request.ticker(), request.question());

        state.addExecution(new AgentExecution(
                AgentType.NEWS,
                AgentExecutionStatus.COMPLETED,
                newsResult.getMessage(),
                elapsedDurationMs(startedAt),
                newsResult.getTokenUsage()
        ));
        state.putStructuredOutput(AgentType.NEWS, newsResult.getFinalResponse());
    }

    private void executeTechnicalAnalysis(AnalysisRequest request, AgentExecutionState state) {
        long startedAt = System.nanoTime();
        TechnicalAnalysisResult technicalAnalysisResult = technicalAnalysisAgent.execute(request.ticker(), request.question());

        state.addExecution(new AgentExecution(
                AgentType.TECHNICAL_ANALYSIS,
                AgentExecutionStatus.COMPLETED,
                technicalAnalysisResult.getMessage(),
                elapsedDurationMs(startedAt),
                technicalAnalysisResult.getTokenUsage()
        ));
        state.putStructuredOutput(AgentType.TECHNICAL_ANALYSIS, technicalAnalysisResult.getFinalResponse());
    }

    private String composeAnswer(AnalysisRequest request, ExecutionPlan executionPlan, AgentExecutionState state) {
        if (!state.hasStructuredOutputs()) {
            state.addExecution(new AgentExecution(
                    AgentType.SYNTHESIS,
                    AgentExecutionStatus.SKIPPED,
                    "Synthesis skipped because no structured outputs were available.",
                    0,
                    null
            ));
            return "I could not complete the requested analysis with the currently available agent outputs.";
        }

        long synthesisStartedAt = System.nanoTime();
        SynthesisResult synthesisResult = synthesisAgent.synthesize(
                request,
                structuredOutput(state, AgentType.MARKET_DATA, MarketSnapshot.class),
                structuredOutput(state, AgentType.FUNDAMENTALS, FundamentalsSnapshot.class),
                structuredOutput(state, AgentType.NEWS, NewsSnapshot.class),
                structuredOutput(state, AgentType.TECHNICAL_ANALYSIS, TechnicalAnalysisSnapshot.class)
        );

        state.addExecution(new AgentExecution(
                AgentType.SYNTHESIS,
                AgentExecutionStatus.COMPLETED,
                "Synthesis completed.",
                elapsedDurationMs(synthesisStartedAt),
                synthesisResult.tokenUsage()
        ));

        return synthesisResult.finalAnswer();
    }

    private <T> T structuredOutput(AgentExecutionState state, AgentType agentType, Class<T> outputType) {
        Object output = state.structuredOutput(agentType);
        if (outputType.isInstance(output)) {
            return outputType.cast(output);
        }
        return null;
    }

    private long elapsedDurationMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }
}
