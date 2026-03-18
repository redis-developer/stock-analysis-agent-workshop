package com.redis.stockanalysisagent.memory.service;

import com.redis.agentmemory.MemoryAPIClient;
import com.redis.agentmemory.exceptions.MemoryClientException;
import com.redis.agentmemory.models.longtermemory.MemoryRecordResults;
import com.redis.agentmemory.models.longtermemory.SearchRequest;
import com.redis.agentmemory.models.workingmemory.MemoryMessage;
import com.redis.agentmemory.models.workingmemory.SessionListResponse;
import com.redis.agentmemory.models.workingmemory.WorkingMemory;
import com.redis.agentmemory.models.workingmemory.WorkingMemoryResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentMemoryService {

    private final MemoryAPIClient client;
    private final String namespace;

    public AgentMemoryService(
            MemoryAPIClient client,
            @Value("${agent-memory.server.namespace:stock-analysis}") String namespace
    ) {
        this.client = client;
        this.namespace = namespace;
    }

    public WorkingMemoryResponse getWorkingMemory(String sessionId, String userId, String modelName) {
        return call("get working memory", () -> client.workingMemory().getWorkingMemory(
                sessionId,
                userId,
                namespace,
                modelName,
                null
        ));
    }

    public List<String> listSessions() {
        SessionListResponse response = call("list working-memory sessions", () -> client.workingMemory().listSessions());
        return response != null && response.getSessions() != null ? response.getSessions() : List.of();
    }

    public void appendMessagesToWorkingMemory(
            String sessionId,
            List<MemoryMessage> messages,
            String userId,
            String modelName
    ) {
        run("append working-memory messages", () -> client.workingMemory().appendMessagesToWorkingMemory(
                sessionId,
                messages,
                namespace,
                modelName,
                null,
                userId
        ));
    }

    public void putWorkingMemory(
            String sessionId,
            WorkingMemory memory,
            String userId,
            String modelName
    ) {
        run("put working memory", () -> client.workingMemory().putWorkingMemory(
                sessionId,
                memory,
                userId,
                namespace,
                modelName,
                null
        ));
    }

    public void deleteWorkingMemory(String sessionId, String userId) {
        run("delete working memory", () -> client.workingMemory().deleteWorkingMemory(sessionId, userId, namespace));
    }

    public MemoryRecordResults searchLongTermMemory(String text, String userId, int limit) {
        SearchRequest request = SearchRequest.builder()
                .text(text)
                .userId(userId)
                .limit(limit)
                .build();
        return call("search long-term memory", () -> client.longTermMemory().searchLongTermMemories(request));
    }

    public String namespace() {
        return namespace;
    }

    private <T> T call(String action, MemoryCall<T> operation) {
        try {
            return operation.execute();
        } catch (MemoryClientException e) {
            throw new RuntimeException("Failed to " + action, e);
        }
    }

    private void run(String action, MemoryAction operation) {
        try {
            operation.execute();
        } catch (MemoryClientException e) {
            throw new RuntimeException("Failed to " + action, e);
        }
    }

    @FunctionalInterface
    private interface MemoryCall<T> {
        T execute() throws MemoryClientException;
    }

    @FunctionalInterface
    private interface MemoryAction {
        void execute() throws MemoryClientException;
    }
}
