package com.redis.stockanalysisagent.agent.orchestration;

import com.redis.stockanalysisagent.agent.coordinatoragent.ExecutionPlan;
import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsAgent;
import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsSnapshot;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketDataAgent;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import com.redis.stockanalysisagent.agent.newsagent.NewsAgent;
import com.redis.stockanalysisagent.agent.newsagent.NewsSnapshot;
import com.redis.stockanalysisagent.agent.synthesisagent.SynthesisAgent;
import com.redis.stockanalysisagent.agent.synthesisagent.SynthesisResult;
import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisAgent;
import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

@Component
class AgentAnswerComposer {

    private final MarketDataAgent marketDataAgent;
    private final FundamentalsAgent fundamentalsAgent;
    private final NewsAgent newsAgent;
    private final TechnicalAnalysisAgent technicalAnalysisAgent;
    private final SynthesisAgent synthesisAgent;

    AgentAnswerComposer(
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

    String compose(AnalysisRequest request, ExecutionPlan executionPlan, AgentExecutionState state) {
        String directAnswer = resolveDirectAnswer(executionPlan, state);
        if (directAnswer != null) {
            return directAnswer;
        }

        if (state.hasStructuredOutputs()) {
            long synthesisStartedAt = System.nanoTime();
            SynthesisResult synthesisResult = synthesisAgent.synthesize(
                    request,
                    executionPlan,
                    structuredOutput(state, AgentType.MARKET_DATA, MarketSnapshot.class),
                    structuredOutput(state, AgentType.FUNDAMENTALS, FundamentalsSnapshot.class),
                    structuredOutput(state, AgentType.NEWS, NewsSnapshot.class),
                    structuredOutput(state, AgentType.TECHNICAL_ANALYSIS, TechnicalAnalysisSnapshot.class),
                    state.agentExecutions()
            );

            state.addExecution(completedExecution(
                    AgentType.SYNTHESIS,
                    elapsedDurationMs(synthesisStartedAt),
                    synthesisSummary(state),
                    synthesisResult.tokenUsage()
            ));

            return synthesisResult.finalAnswer();
        }

        if (executionPlan.requiresSynthesis()) {
            state.addExecution(new AgentExecution(
                    AgentType.SYNTHESIS,
                    AgentExecutionStatus.SKIPPED,
                    "Synthesis skipped.",
                    0,
                    null
            ));
        }

        if (!state.limitations().isEmpty()) {
            return "I could not complete the requested analysis. %s".formatted(String.join(" ", state.limitations()));
        }

        return "I could not complete the requested analysis with the currently available agent outputs.";
    }

    <T> T structuredOutput(AgentExecutionState state, AgentType agentType, Class<T> outputType) {
        Object output = state.structuredOutput(agentType);
        if (outputType.isInstance(output)) {
            return outputType.cast(output);
        }
        return null;
    }

    private String resolveDirectAnswer(ExecutionPlan executionPlan, AgentExecutionState state) {
        if (executionPlan.requiresSynthesis() || executionPlan.selectedAgents().size() != 1) {
            return null;
        }

        AgentType selectedAgent = executionPlan.selectedAgents().getFirst();
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

    private <T> String directAnswer(
            AgentExecutionState state,
            AgentType agentType,
            Class<T> outputType,
            Function<T, String> fallback
    ) {
        T output = structuredOutput(state, agentType, outputType);
        if (output == null) {
            return null;
        }

        String directAnswer = state.directAnswer(agentType);
        if (directAnswer != null && !directAnswer.isBlank()) {
            return directAnswer;
        }

        return fallback.apply(output);
    }

    private AgentExecution completedExecution(
            AgentType agentType,
            long durationMs,
            String summary,
            TokenUsageSummary tokenUsage
    ) {
        return new AgentExecution(
                agentType,
                AgentExecutionStatus.COMPLETED,
                normalizeSummary(summary, "%s completed.".formatted(agentLabel(agentType))),
                durationMs,
                tokenUsage
        );
    }

    private String synthesisSummary(AgentExecutionState state) {
        List<String> contributingAgents = state.agentExecutions().stream()
                .map(AgentExecution::agentType)
                .filter(agentType -> agentType != AgentType.SYNTHESIS)
                .distinct()
                .map(this::agentLabel)
                .toList();

        if (contributingAgents.isEmpty()) {
            return "Combined the available specialist outputs into the final response.";
        }

        return "Combined outputs from %s into the final response."
                .formatted(String.join(", ", contributingAgents));
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

    private String normalizeSummary(String summary, String fallback) {
        String normalized = summary == null ? "" : summary.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (!normalized.isBlank()) {
            return normalized;
        }

        return fallback;
    }

    private long elapsedDurationMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }
}
