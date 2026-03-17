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
import com.redis.stockanalysisagent.agent.newsagent.NewsAgent;
import com.redis.stockanalysisagent.agent.newsagent.NewsResult;
import com.redis.stockanalysisagent.agent.newsagent.NewsSnapshot;
import com.redis.stockanalysisagent.agent.synthesisagent.SynthesisAgent;
import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisAgent;
import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisResult;
import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisSnapshot;
import com.redis.stockanalysisagent.api.AnalysisRequest;
import com.redis.stockanalysisagent.api.AnalysisResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

@Service
public class AgentOrchestrationService {

    private final CoordinatorAgent coordinatorAgent;
    private final MarketDataAgent marketDataAgent;
    private final FundamentalsAgent fundamentalsAgent;
    private final NewsAgent newsAgent;
    private final TechnicalAnalysisAgent technicalAnalysisAgent;
    private final SynthesisAgent synthesisAgent;
    private final TaskExecutor agentTaskExecutor;

    public AgentOrchestrationService(
            CoordinatorAgent coordinatorAgent,
            MarketDataAgent marketDataAgent,
            FundamentalsAgent fundamentalsAgent,
            NewsAgent newsAgent,
            TechnicalAnalysisAgent technicalAnalysisAgent,
            SynthesisAgent synthesisAgent,
            @Qualifier("agentTaskExecutor") TaskExecutor agentTaskExecutor
    ) {
        this.coordinatorAgent = coordinatorAgent;
        this.marketDataAgent = marketDataAgent;
        this.fundamentalsAgent = fundamentalsAgent;
        this.newsAgent = newsAgent;
        this.technicalAnalysisAgent = technicalAnalysisAgent;
        this.synthesisAgent = synthesisAgent;
        this.agentTaskExecutor = agentTaskExecutor;
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
                    null,
                    null,
                    resolveCoordinatorMessage(routingDecision),
                    List.of("Coordinator could not produce an execution plan.")
            );
        }

        ExecutionPlan executionPlan = coordinatorAgent.createPlan(routingDecision);
        ExecutionState state = executeSelectedAgents(request, executionPlan);
        String answer = buildAnswer(request, executionPlan, state);

        return new AnalysisResponse(
                request.ticker().toUpperCase(),
                request.question(),
                OffsetDateTime.now(),
                executionPlan,
                List.copyOf(state.agentExecutions),
                state.marketSnapshot,
                state.fundamentalsSnapshot,
                state.newsSnapshot,
                state.technicalAnalysisSnapshot,
                answer,
                List.copyOf(state.limitations)
        );
    }

    private ExecutionState executeSelectedAgents(AnalysisRequest request, ExecutionPlan executionPlan) {
        ExecutionState state = new ExecutionState();
        Map<AgentType, CompletableFuture<AgentExecutionOutcome>> executionFutures = new LinkedHashMap<>();
        CompletableFuture<AgentExecutionOutcome> marketFuture = null;

        if (executionPlan.selectedAgents().contains(AgentType.MARKET_DATA)) {
            marketFuture = submitAsync(AgentType.MARKET_DATA, () -> executeMarketData(request));
            executionFutures.put(AgentType.MARKET_DATA, marketFuture);
        }

        for (AgentType agentType : executionPlan.selectedAgents()) {
            if (agentType == AgentType.SYNTHESIS) {
                continue;
            }

            if (agentType == AgentType.MARKET_DATA) {
                continue;
            }

            CompletableFuture<AgentExecutionOutcome> future = switch (agentType) {
                case FUNDAMENTALS -> marketFuture != null
                        ? marketFuture.thenApplyAsync(
                                marketOutcome -> executeFundamentals(request, marketOutcome.marketSnapshot),
                                agentTaskExecutor
                        ).exceptionally(ex -> failedOutcome(AgentType.FUNDAMENTALS, ex))
                        : submitAsync(AgentType.FUNDAMENTALS, () -> executeFundamentals(request, null));
                case NEWS -> submitAsync(AgentType.NEWS, () -> executeNews(request));
                case TECHNICAL_ANALYSIS -> submitAsync(
                        AgentType.TECHNICAL_ANALYSIS,
                        () -> executeTechnicalAnalysis(request)
                );
                case MARKET_DATA -> executionFutures.get(AgentType.MARKET_DATA);
                case SYNTHESIS -> CompletableFuture.completedFuture(failedOutcome(
                        AgentType.SYNTHESIS,
                        new IllegalStateException("Synthesis should execute only after the specialized agents finish.")
                ));
            };

            executionFutures.put(agentType, future);
        }

        CompletableFuture.allOf(executionFutures.values().toArray(CompletableFuture[]::new)).join();

        for (AgentType agentType : executionPlan.selectedAgents()) {
            if (agentType == AgentType.SYNTHESIS) {
                continue;
            }

            AgentExecutionOutcome outcome = executionFutures.get(agentType).join();
            mergeOutcome(state, outcome);
        }

        return state;
    }

    private CompletableFuture<AgentExecutionOutcome> submitAsync(
            AgentType agentType,
            Supplier<AgentExecutionOutcome> task
    ) {
        return CompletableFuture.supplyAsync(task, agentTaskExecutor)
                .exceptionally(ex -> failedOutcome(agentType, ex));
    }

    private AgentExecutionOutcome executeMarketData(AnalysisRequest request) {
        try {
            MarketDataResult marketDataResult = marketDataAgent.execute(request.ticker(), request.question());
            return AgentExecutionOutcome.completed(
                    new AgentExecution(
                            AgentType.MARKET_DATA,
                            AgentExecutionStatus.COMPLETED,
                            "Market Data Agent used its market-data tools to fetch a current snapshot."
                    ),
                    null,
                    marketDataResult.getMessage(),
                    null,
                    marketDataResult.getFinalResponse(),
                    null,
                    null,
                    null
            );
        } catch (RuntimeException ex) {
            return failedOutcome(AgentType.MARKET_DATA, ex);
        }
    }

    private AgentExecutionOutcome executeFundamentals(AnalysisRequest request, MarketSnapshot marketSnapshot) {
        try {
            FundamentalsResult fundamentalsResult = marketSnapshot != null
                    ? fundamentalsAgent.execute(request.ticker(), request.question(), marketSnapshot)
                    : fundamentalsAgent.execute(request.ticker(), request.question());

            return AgentExecutionOutcome.completed(
                    new AgentExecution(
                            AgentType.FUNDAMENTALS,
                            AgentExecutionStatus.COMPLETED,
                            marketSnapshot != null
                                    ? "Fundamentals Agent used its fundamentals tool with market-price context."
                                    : "Fundamentals Agent used its fundamentals tool to fetch a normalized snapshot."
                    ),
                    null,
                    null,
                    fundamentalsResult.getMessage(),
                    null,
                    fundamentalsResult.getFinalResponse(),
                    null,
                    null
            );
        } catch (RuntimeException ex) {
            return failedOutcome(AgentType.FUNDAMENTALS, ex);
        }
    }

    private AgentExecutionOutcome executeNews(AnalysisRequest request) {
        try {
            NewsResult newsResult = newsAgent.execute(request.ticker(), request.question());
            return AgentExecutionOutcome.completed(
                    new AgentExecution(
                            AgentType.NEWS,
                            AgentExecutionStatus.COMPLETED,
                            "News Agent collected recent company-event signals and web news relevant to the requested ticker."
                    ),
                    null,
                    null,
                    null,
                    null,
                    null,
                    newsResult.getFinalResponse(),
                    null
            );
        } catch (RuntimeException ex) {
            return failedOutcome(AgentType.NEWS, ex);
        }
    }

    private AgentExecutionOutcome executeTechnicalAnalysis(AnalysisRequest request) {
        try {
            TechnicalAnalysisResult technicalAnalysisResult = technicalAnalysisAgent.execute(request.ticker());
            return AgentExecutionOutcome.completed(
                    new AgentExecution(
                            AgentType.TECHNICAL_ANALYSIS,
                            AgentExecutionStatus.COMPLETED,
                            "Technical Analysis Agent calculated SMA, EMA, and RSI from Twelve Data price history."
                    ),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    technicalAnalysisResult.getFinalResponse()
            );
        } catch (RuntimeException ex) {
            return failedOutcome(AgentType.TECHNICAL_ANALYSIS, ex);
        }
    }

    private AgentExecutionOutcome failedOutcome(AgentType agentType, Throwable throwable) {
        Throwable normalizedThrowable = unwrapThrowable(throwable);
        String normalizedError = normalizeErrorMessage(normalizedThrowable);
        return new AgentExecutionOutcome(
                new AgentExecution(
                        agentType,
                        AgentExecutionStatus.FAILED,
                        "%s failed: %s".formatted(agentLabel(agentType), normalizedError)
                ),
                "%s failed: %s".formatted(agentType, normalizedError),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private void mergeOutcome(ExecutionState state, AgentExecutionOutcome outcome) {
        state.agentExecutions.add(outcome.execution);
        if (outcome.limitations != null) {
            state.limitations.add(outcome.limitations);
        }
        if (outcome.marketDirectAnswer != null && !outcome.marketDirectAnswer.isBlank()) {
            state.marketDirectAnswer = outcome.marketDirectAnswer;
        }
        if (outcome.marketSnapshot != null) {
            state.marketSnapshot = outcome.marketSnapshot;
        }
        if (outcome.fundamentalsDirectAnswer != null && !outcome.fundamentalsDirectAnswer.isBlank()) {
            state.fundamentalsDirectAnswer = outcome.fundamentalsDirectAnswer;
        }
        if (outcome.fundamentalsSnapshot != null) {
            state.fundamentalsSnapshot = outcome.fundamentalsSnapshot;
        }
        if (outcome.newsSnapshot != null) {
            state.newsSnapshot = outcome.newsSnapshot;
        }
        if (outcome.technicalAnalysisSnapshot != null) {
            state.technicalAnalysisSnapshot = outcome.technicalAnalysisSnapshot;
        }
    }

    private String buildAnswer(AnalysisRequest request, ExecutionPlan executionPlan, ExecutionState state) {
        if (shouldUseDirectMarketAnswer(executionPlan, state.marketSnapshot)) {
            if (state.marketDirectAnswer != null && !state.marketDirectAnswer.isBlank()) {
                return state.marketDirectAnswer;
            }
            return marketDataAgent.createDirectAnswer(state.marketSnapshot);
        }

        if (shouldUseDirectFundamentalsAnswer(executionPlan, state.fundamentalsSnapshot)) {
            if (state.fundamentalsDirectAnswer != null && !state.fundamentalsDirectAnswer.isBlank()) {
                return state.fundamentalsDirectAnswer;
            }
            return fundamentalsAgent.createDirectAnswer(state.fundamentalsSnapshot);
        }

        if (shouldUseDirectNewsAnswer(executionPlan, state.newsSnapshot)) {
            return newsAgent.createDirectAnswer(state.newsSnapshot);
        }

        if (shouldUseDirectTechnicalAnswer(executionPlan, state.technicalAnalysisSnapshot)) {
            return technicalAnalysisAgent.createDirectAnswer(state.technicalAnalysisSnapshot);
        }

        if (hasAnyStructuredOutputs(state)) {
            String synthesizedAnswer = synthesisAgent.synthesize(
                    request,
                    executionPlan,
                    state.marketSnapshot,
                    state.fundamentalsSnapshot,
                    state.newsSnapshot,
                    state.technicalAnalysisSnapshot,
                    state.agentExecutions
            );

            if (executionPlan.requiresSynthesis()) {
                state.agentExecutions.add(new AgentExecution(
                        AgentType.SYNTHESIS,
                        AgentExecutionStatus.COMPLETED,
                        "Synthesis Agent combined the available agent outputs into the final response."
                ));
            }

            return synthesizedAnswer;
        }

        if (executionPlan.requiresSynthesis()) {
            state.agentExecutions.add(new AgentExecution(
                    AgentType.SYNTHESIS,
                    AgentExecutionStatus.SKIPPED,
                    "Synthesis was skipped because no specialized agent outputs were available."
            ));
        }

        if (!state.limitations.isEmpty()) {
            return "I could not complete the requested analysis. %s".formatted(String.join(" ", state.limitations));
        }

        return "I could not complete the requested analysis with the currently available agent outputs.";
    }

    private boolean hasAnyStructuredOutputs(ExecutionState state) {
        return state.marketSnapshot != null
                || state.fundamentalsSnapshot != null
                || state.newsSnapshot != null
                || state.technicalAnalysisSnapshot != null;
    }

    private String agentLabel(AgentType agentType) {
        return switch (agentType) {
            case MARKET_DATA -> "Market Data Agent";
            case FUNDAMENTALS -> "Fundamentals Agent";
            case NEWS -> "News Agent";
            case TECHNICAL_ANALYSIS -> "Technical Analysis Agent";
            case SYNTHESIS -> "Synthesis Agent";
        };
    }

    private Throwable unwrapThrowable(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private String normalizeErrorMessage(Throwable ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.replace('\n', ' ').trim();
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

    private boolean shouldUseDirectNewsAnswer(ExecutionPlan executionPlan, NewsSnapshot newsSnapshot) {
        return newsSnapshot != null
                && !executionPlan.requiresSynthesis()
                && executionPlan.selectedAgents().size() == 1
                && executionPlan.selectedAgents().contains(AgentType.NEWS);
    }

    private boolean shouldUseDirectTechnicalAnswer(
            ExecutionPlan executionPlan,
            TechnicalAnalysisSnapshot technicalAnalysisSnapshot
    ) {
        return technicalAnalysisSnapshot != null
                && !executionPlan.requiresSynthesis()
                && executionPlan.selectedAgents().size() == 1
                && executionPlan.selectedAgents().contains(AgentType.TECHNICAL_ANALYSIS);
    }

    private static class ExecutionState {
        private final List<AgentExecution> agentExecutions = new ArrayList<>();
        private final List<String> limitations = new ArrayList<>();
        private String marketDirectAnswer;
        private String fundamentalsDirectAnswer;
        private MarketSnapshot marketSnapshot;
        private FundamentalsSnapshot fundamentalsSnapshot;
        private NewsSnapshot newsSnapshot;
        private TechnicalAnalysisSnapshot technicalAnalysisSnapshot;
    }

    private static class AgentExecutionOutcome {
        private final AgentExecution execution;
        private final String limitations;
        private final String marketDirectAnswer;
        private final String fundamentalsDirectAnswer;
        private final MarketSnapshot marketSnapshot;
        private final FundamentalsSnapshot fundamentalsSnapshot;
        private final NewsSnapshot newsSnapshot;
        private final TechnicalAnalysisSnapshot technicalAnalysisSnapshot;

        private AgentExecutionOutcome(
                AgentExecution execution,
                String limitations,
                String marketDirectAnswer,
                String fundamentalsDirectAnswer,
                MarketSnapshot marketSnapshot,
                FundamentalsSnapshot fundamentalsSnapshot,
                NewsSnapshot newsSnapshot,
                TechnicalAnalysisSnapshot technicalAnalysisSnapshot
        ) {
            this.execution = execution;
            this.limitations = limitations;
            this.marketDirectAnswer = marketDirectAnswer;
            this.fundamentalsDirectAnswer = fundamentalsDirectAnswer;
            this.marketSnapshot = marketSnapshot;
            this.fundamentalsSnapshot = fundamentalsSnapshot;
            this.newsSnapshot = newsSnapshot;
            this.technicalAnalysisSnapshot = technicalAnalysisSnapshot;
        }

        private static AgentExecutionOutcome completed(
                AgentExecution execution,
                String limitation,
                String marketDirectAnswer,
                String fundamentalsDirectAnswer,
                MarketSnapshot marketSnapshot,
                FundamentalsSnapshot fundamentalsSnapshot,
                NewsSnapshot newsSnapshot,
                TechnicalAnalysisSnapshot technicalAnalysisSnapshot
        ) {
            return new AgentExecutionOutcome(
                    execution,
                    limitation,
                    marketDirectAnswer,
                    fundamentalsDirectAnswer,
                    marketSnapshot,
                    fundamentalsSnapshot,
                    newsSnapshot,
                    technicalAnalysisSnapshot
            );
        }
    }
}
