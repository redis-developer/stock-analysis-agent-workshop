package com.redis.stockanalysisagent.agent.orchestration;

import com.redis.stockanalysisagent.agent.coordinatoragent.ExecutionPlan;
import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsSnapshot;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import com.redis.stockanalysisagent.agent.newsagent.NewsSnapshot;
import com.redis.stockanalysisagent.agent.synthesisagent.SynthesisAgent;
import com.redis.stockanalysisagent.agent.synthesisagent.SynthesisResult;
import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class AgentAnswerComposer {

    private final SynthesisAgent synthesisAgent;

    AgentAnswerComposer(SynthesisAgent synthesisAgent) {
        this.synthesisAgent = synthesisAgent;
    }

    String compose(AnalysisRequest request, ExecutionPlan executionPlan, AgentExecutionState state) {
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

        state.addExecution(new AgentExecution(
                AgentType.SYNTHESIS,
                AgentExecutionStatus.SKIPPED,
                "Synthesis skipped because no structured outputs were available.",
                0,
                null
        ));

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
