package com.redis.stockanalysisagent.agent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class AgentExecutionState {

    private final List<AgentExecution> agentExecutions = new ArrayList<>();
    private final List<String> limitations = new ArrayList<>();
    private final Map<AgentType, Object> structuredOutputs = new EnumMap<>(AgentType.class);
    private final Map<AgentType, String> directAnswers = new EnumMap<>(AgentType.class);

    List<AgentExecution> agentExecutions() {
        return agentExecutions;
    }

    List<String> limitations() {
        return limitations;
    }

    void addExecution(AgentExecution execution) {
        agentExecutions.add(execution);
    }

    void addLimitation(String limitation) {
        limitations.add(limitation);
    }

    void putStructuredOutput(AgentType agentType, Object output) {
        structuredOutputs.put(agentType, output);
    }

    void putDirectAnswer(AgentType agentType, String directAnswer) {
        directAnswers.put(agentType, directAnswer);
    }

    Object structuredOutput(AgentType agentType) {
        return structuredOutputs.get(agentType);
    }

    String directAnswer(AgentType agentType) {
        return directAnswers.get(agentType);
    }

    boolean hasStructuredOutputs() {
        return !structuredOutputs.isEmpty();
    }
}
