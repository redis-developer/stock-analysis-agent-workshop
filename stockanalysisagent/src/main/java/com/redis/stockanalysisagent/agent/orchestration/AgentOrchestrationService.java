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
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.redis.stockanalysisagent.observability.OrchestrationObservability.agentObservation;
import static com.redis.stockanalysisagent.observability.OrchestrationObservability.enrichWithAgentExecution;

@Service
public class AgentOrchestrationService {

    private final MarketDataAgent marketDataAgent;
    private final FundamentalsAgent fundamentalsAgent;
    private final NewsAgent newsAgent;
    private final TechnicalAnalysisAgent technicalAnalysisAgent;
    private final SynthesisAgent synthesisAgent;
    private final ObservationRegistry observationRegistry;

    public AgentOrchestrationService(
            MarketDataAgent marketDataAgent,
            FundamentalsAgent fundamentalsAgent,
            NewsAgent newsAgent,
            TechnicalAnalysisAgent technicalAnalysisAgent,
            SynthesisAgent synthesisAgent,
            ObservationRegistry observationRegistry
    ) {
        this.marketDataAgent = marketDataAgent;
        this.fundamentalsAgent = fundamentalsAgent;
        this.newsAgent = newsAgent;
        this.technicalAnalysisAgent = technicalAnalysisAgent;
        this.synthesisAgent = synthesisAgent;
        this.observationRegistry = observationRegistry;
    }

    public AnalysisResponse processRequest(AnalysisRequest request, ExecutionPlan executionPlan) {
        List<AgentExecution> executions = new ArrayList<>();
        MarketSnapshot marketSnapshot = null;
        FundamentalsSnapshot fundamentalsSnapshot = null;
        NewsSnapshot newsSnapshot = null;
        TechnicalAnalysisSnapshot technicalAnalysisSnapshot = null;

        for (AgentType agentType : executionPlan.selectedAgents()) {
            if (agentType == AgentType.SYNTHESIS) {
                continue;
            }

            switch (agentType) {
                case MARKET_DATA -> {
                    Observation observation = agentObservation(observationRegistry, AgentType.MARKET_DATA).start();
                    long startedAt = System.nanoTime();
                    try {
                        MarketDataResult result = marketDataAgent.execute(request.ticker(), request.question());
                        marketSnapshot = result.getFinalResponse();
                        AgentExecution execution = new AgentExecution(
                                AgentType.MARKET_DATA,
                                result.getMessage(),
                                elapsedDurationMs(startedAt),
                                result.getTokenUsage()
                        );
                        enrichWithAgentExecution(observation, execution);
                        executions.add(execution);
                    } catch (Throwable t) {
                        observation.error(t);
                        throw t;
                    } finally {
                        observation.stop();
                    }
                }
                case FUNDAMENTALS -> {
                    Observation observation = agentObservation(observationRegistry, AgentType.FUNDAMENTALS).start();
                    long startedAt = System.nanoTime();
                    try {
                        FundamentalsResult result = fundamentalsAgent.execute(request.ticker(), request.question(), marketSnapshot);
                        fundamentalsSnapshot = result.getFinalResponse();
                        AgentExecution execution = new AgentExecution(
                                AgentType.FUNDAMENTALS,
                                result.getMessage(),
                                elapsedDurationMs(startedAt),
                                result.getTokenUsage()
                        );
                        enrichWithAgentExecution(observation, execution);
                        executions.add(execution);
                    } catch (Throwable t) {
                        observation.error(t);
                        throw t;
                    } finally {
                        observation.stop();
                    }
                }
                case NEWS -> {
                    Observation observation = agentObservation(observationRegistry, AgentType.NEWS).start();
                    long startedAt = System.nanoTime();
                    try {
                        NewsResult result = newsAgent.execute(request.ticker(), request.question());
                        newsSnapshot = result.getFinalResponse();
                        AgentExecution execution = new AgentExecution(
                                AgentType.NEWS,
                                result.getMessage(),
                                elapsedDurationMs(startedAt),
                                result.getTokenUsage()
                        );
                        enrichWithAgentExecution(observation, execution);
                        executions.add(execution);
                    } catch (Throwable t) {
                        observation.error(t);
                        throw t;
                    } finally {
                        observation.stop();
                    }
                }
                case TECHNICAL_ANALYSIS -> {
                    Observation observation = agentObservation(observationRegistry, AgentType.TECHNICAL_ANALYSIS).start();
                    long startedAt = System.nanoTime();
                    try {
                        TechnicalAnalysisResult result = technicalAnalysisAgent.execute(request.ticker(), request.question());
                        technicalAnalysisSnapshot = result.getFinalResponse();
                        AgentExecution execution = new AgentExecution(
                                AgentType.TECHNICAL_ANALYSIS,
                                result.getMessage(),
                                elapsedDurationMs(startedAt),
                                result.getTokenUsage()
                        );
                        enrichWithAgentExecution(observation, execution);
                        executions.add(execution);
                    } catch (Throwable t) {
                        observation.error(t);
                        throw t;
                    } finally {
                        observation.stop();
                    }
                }
                case SYNTHESIS -> throw new IllegalStateException(
                        "Synthesis should execute only after the specialized agents finish."
                );
            }
        }

        Observation observation = agentObservation(observationRegistry, AgentType.SYNTHESIS).start();
        long synthesisStartedAt = System.nanoTime();
        try {
            SynthesisResult synthesisResult = synthesisAgent.synthesize(
                    request,
                    marketSnapshot,
                    fundamentalsSnapshot,
                    newsSnapshot,
                    technicalAnalysisSnapshot
            );
            AgentExecution execution = new AgentExecution(
                    AgentType.SYNTHESIS,
                    "Synthesis completed.",
                    elapsedDurationMs(synthesisStartedAt),
                    synthesisResult.tokenUsage()
            );
            enrichWithAgentExecution(observation, execution);
            executions.add(execution);
            return AnalysisResponse.completed(
                    executions,
                    synthesisResult.finalAnswer(),
                    synthesisResult.semanticCacheStored()
            );
        } catch (Throwable t) {
            observation.error(t);
            throw t;
        } finally {
            observation.stop();
        }
    }

    private long elapsedDurationMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }
}
