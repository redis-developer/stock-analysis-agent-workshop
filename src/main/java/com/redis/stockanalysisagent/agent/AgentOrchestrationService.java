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
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
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
                structuredOutput(state, AgentType.MARKET_DATA, MarketSnapshot.class),
                structuredOutput(state, AgentType.FUNDAMENTALS, FundamentalsSnapshot.class),
                structuredOutput(state, AgentType.NEWS, NewsSnapshot.class),
                structuredOutput(state, AgentType.TECHNICAL_ANALYSIS, TechnicalAnalysisSnapshot.class),
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
                                marketOutcome -> executeFundamentals(
                                        request,
                                        marketOutcome.output() instanceof MarketSnapshot snapshot ? snapshot : null
                                ),
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
                    marketDataResult.getFinalResponse(),
                    marketDataResult.getMessage()
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
                    fundamentalsResult.getFinalResponse(),
                    fundamentalsResult.getMessage()
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
                            "News Agent used its news tool to fetch a hybrid news snapshot."
                    ),
                    newsResult.getFinalResponse(),
                    newsResult.getMessage()
            );
        } catch (RuntimeException ex) {
            return failedOutcome(AgentType.NEWS, ex);
        }
    }

    private AgentExecutionOutcome executeTechnicalAnalysis(AnalysisRequest request) {
        try {
            TechnicalAnalysisResult technicalAnalysisResult = technicalAnalysisAgent.execute(request.ticker(), request.question());
            return AgentExecutionOutcome.completed(
                    new AgentExecution(
                            AgentType.TECHNICAL_ANALYSIS,
                            AgentExecutionStatus.COMPLETED,
                            "Technical Analysis Agent used its technical-analysis tool to fetch a normalized snapshot."
                    ),
                    technicalAnalysisResult.getFinalResponse(),
                    technicalAnalysisResult.getMessage()
            );
        } catch (RuntimeException ex) {
            return failedOutcome(AgentType.TECHNICAL_ANALYSIS, ex);
        }
    }

    private AgentExecutionOutcome failedOutcome(AgentType agentType, Throwable throwable) {
        Throwable normalizedThrowable = unwrapThrowable(throwable);
        String normalizedError = normalizeErrorMessage(normalizedThrowable);
        return AgentExecutionOutcome.failed(
                new AgentExecution(
                        agentType,
                        AgentExecutionStatus.FAILED,
                        "%s failed: %s".formatted(agentLabel(agentType), normalizedError)
                ),
                "%s failed: %s".formatted(agentType, normalizedError)
        );
    }

    private void mergeOutcome(ExecutionState state, AgentExecutionOutcome outcome) {
        state.agentExecutions.add(outcome.execution());
        if (outcome.limitation() != null) {
            state.limitations.add(outcome.limitation());
        }
        if (outcome.output() != null) {
            state.structuredOutputs.put(outcome.execution().agentType(), outcome.output());
        }
        if (outcome.directAnswer() != null && !outcome.directAnswer().isBlank()) {
            state.directAnswers.put(outcome.execution().agentType(), outcome.directAnswer());
        }
    }

    private String buildAnswer(AnalysisRequest request, ExecutionPlan executionPlan, ExecutionState state) {
        String directAnswer = resolveDirectAnswer(executionPlan, state);
        if (directAnswer != null) {
            return directAnswer;
        }

        if (hasAnyStructuredOutputs(state)) {
            MarketSnapshot marketSnapshot = structuredOutput(state, AgentType.MARKET_DATA, MarketSnapshot.class);
            FundamentalsSnapshot fundamentalsSnapshot = structuredOutput(state, AgentType.FUNDAMENTALS, FundamentalsSnapshot.class);
            NewsSnapshot newsSnapshot = structuredOutput(state, AgentType.NEWS, NewsSnapshot.class);
            TechnicalAnalysisSnapshot technicalAnalysisSnapshot = structuredOutput(
                    state,
                    AgentType.TECHNICAL_ANALYSIS,
                    TechnicalAnalysisSnapshot.class
            );
            String synthesizedAnswer = synthesisAgent.synthesize(
                    request,
                    executionPlan,
                    marketSnapshot,
                    fundamentalsSnapshot,
                    newsSnapshot,
                    technicalAnalysisSnapshot,
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
        return !state.structuredOutputs.isEmpty();
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

    private String resolveDirectAnswer(ExecutionPlan executionPlan, ExecutionState state) {
        if (executionPlan.requiresSynthesis() || executionPlan.selectedAgents().size() != 1) {
            return null;
        }

        AgentType selectedAgent = executionPlan.selectedAgents().get(0);
        return switch (selectedAgent) {
            case MARKET_DATA -> directAnswer(
                    state,
                    AgentType.MARKET_DATA,
                    MarketSnapshot.class,
                    marketDataAgent::createDirectAnswer
            );
            case FUNDAMENTALS -> directAnswer(
                    state,
                    AgentType.FUNDAMENTALS,
                    FundamentalsSnapshot.class,
                    fundamentalsAgent::createDirectAnswer
            );
            case NEWS -> directAnswer(
                    state,
                    AgentType.NEWS,
                    NewsSnapshot.class,
                    newsAgent::createDirectAnswer
            );
            case TECHNICAL_ANALYSIS -> directAnswer(
                    state,
                    AgentType.TECHNICAL_ANALYSIS,
                    TechnicalAnalysisSnapshot.class,
                    technicalAnalysisAgent::createDirectAnswer
            );
            case SYNTHESIS -> null;
        };
    }

    private <T> T structuredOutput(ExecutionState state, AgentType agentType, Class<T> outputType) {
        Object output = state.structuredOutputs.get(agentType);
        if (outputType.isInstance(output)) {
            return outputType.cast(output);
        }
        return null;
    }

    private <T> String directAnswer(
            ExecutionState state,
            AgentType agentType,
            Class<T> outputType,
            Function<T, String> fallback
    ) {
        T output = structuredOutput(state, agentType, outputType);
        if (output == null) {
            return null;
        }

        String directAnswer = state.directAnswers.get(agentType);
        if (directAnswer != null && !directAnswer.isBlank()) {
            return directAnswer;
        }

        return fallback.apply(output);
    }

    private static class ExecutionState {
        private final List<AgentExecution> agentExecutions = new ArrayList<>();
        private final List<String> limitations = new ArrayList<>();
        private final Map<AgentType, Object> structuredOutputs = new EnumMap<>(AgentType.class);
        private final Map<AgentType, String> directAnswers = new EnumMap<>(AgentType.class);
    }

    private record AgentExecutionOutcome(
            AgentExecution execution,
            String limitation,
            Object output,
            String directAnswer
    ) {
        private static AgentExecutionOutcome completed(AgentExecution execution, Object output, String directAnswer) {
            return new AgentExecutionOutcome(execution, null, output, directAnswer);
        }

        private static AgentExecutionOutcome failed(AgentExecution execution, String limitation) {
            return new AgentExecutionOutcome(execution, limitation, null, null);
        }
    }
}
