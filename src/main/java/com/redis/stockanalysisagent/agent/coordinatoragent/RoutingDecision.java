package com.redis.stockanalysisagent.agent.coordinatoragent;

import com.redis.stockanalysisagent.agent.AgentType;

import java.util.ArrayList;
import java.util.List;

public class RoutingDecision {

    public enum FinishReason {
        NEEDS_MORE_INPUT,
        COMPLETED,
        CANNOT_PROCEED,
        OUT_OF_SCOPE
    }

    private FinishReason finishReason;
    private String nextPrompt;
    private String finalResponse;
    private String conversationId;
    private String resolvedTicker;
    private String resolvedQuestion;
    private List<AgentType> selectedAgents = new ArrayList<>();
    private boolean requiresSynthesis;
    private String reasoning;

    public RoutingDecision() {
    }

    public RoutingDecision(
            FinishReason finishReason,
            String nextPrompt,
            String finalResponse,
            String resolvedTicker,
            String resolvedQuestion,
            List<AgentType> selectedAgents,
            boolean requiresSynthesis,
            String reasoning
    ) {
        this.finishReason = finishReason;
        this.nextPrompt = nextPrompt;
        this.finalResponse = finalResponse;
        this.resolvedTicker = resolvedTicker;
        this.resolvedQuestion = resolvedQuestion;
        this.selectedAgents = selectedAgents;
        this.requiresSynthesis = requiresSynthesis;
        this.reasoning = reasoning;
    }

    public static RoutingDecision of(List<AgentType> selectedAgents, boolean requiresSynthesis, String reasoning) {
        return completed(null, null, selectedAgents, requiresSynthesis, reasoning);
    }

    public static RoutingDecision completed(
            String resolvedTicker,
            String resolvedQuestion,
            List<AgentType> selectedAgents,
            boolean requiresSynthesis,
            String reasoning
    ) {
        return new RoutingDecision(
                FinishReason.COMPLETED,
                null,
                null,
                resolvedTicker,
                resolvedQuestion,
                selectedAgents,
                requiresSynthesis,
                reasoning
        );
    }

    public static RoutingDecision needsMoreInput(String nextPrompt) {
        return new RoutingDecision(
                FinishReason.NEEDS_MORE_INPUT,
                nextPrompt,
                null,
                null,
                null,
                new ArrayList<>(),
                false,
                null
        );
    }

    public static RoutingDecision outOfScope(String finalResponse) {
        return new RoutingDecision(
                FinishReason.OUT_OF_SCOPE,
                null,
                finalResponse,
                null,
                null,
                new ArrayList<>(),
                false,
                null
        );
    }

    public static RoutingDecision cannotProceed(String finalResponse) {
        return new RoutingDecision(
                FinishReason.CANNOT_PROCEED,
                null,
                finalResponse,
                null,
                null,
                new ArrayList<>(),
                false,
                null
        );
    }

    public FinishReason getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(FinishReason finishReason) {
        this.finishReason = finishReason;
    }

    public String getNextPrompt() {
        return nextPrompt;
    }

    public void setNextPrompt(String nextPrompt) {
        this.nextPrompt = nextPrompt;
    }

    public String getFinalResponse() {
        return finalResponse;
    }

    public void setFinalResponse(String finalResponse) {
        this.finalResponse = finalResponse;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getResolvedTicker() {
        return resolvedTicker;
    }

    public void setResolvedTicker(String resolvedTicker) {
        this.resolvedTicker = resolvedTicker;
    }

    public String getResolvedQuestion() {
        return resolvedQuestion;
    }

    public void setResolvedQuestion(String resolvedQuestion) {
        this.resolvedQuestion = resolvedQuestion;
    }

    public List<AgentType> getSelectedAgents() {
        return selectedAgents;
    }

    public void setSelectedAgents(List<AgentType> selectedAgents) {
        this.selectedAgents = selectedAgents;
    }

    public boolean isRequiresSynthesis() {
        return requiresSynthesis;
    }

    public void setRequiresSynthesis(boolean requiresSynthesis) {
        this.requiresSynthesis = requiresSynthesis;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }
}
