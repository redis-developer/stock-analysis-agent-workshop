package com.redis.stockanalysisagent.memory.service;

import com.redis.agentmemory.MemoryAPIClient;
import com.redis.agentmemory.models.common.AckResponse;
import com.redis.agentmemory.models.health.HealthCheckResponse;
import com.redis.agentmemory.models.longtermemory.MemoryRecord;
import com.redis.agentmemory.models.longtermemory.MemoryRecordResults;
import com.redis.agentmemory.models.longtermemory.MemoryType;
import com.redis.agentmemory.models.longtermemory.SearchRequest;
import com.redis.agentmemory.models.workingmemory.MemoryMessage;
import com.redis.agentmemory.models.workingmemory.WorkingMemory;
import com.redis.agentmemory.models.workingmemory.WorkingMemoryResponse;
import com.redis.agentmemory.models.workingmemory.SessionListResponse;
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
        try {
            return client.workingMemory().getWorkingMemory(
                    sessionId,
                    userId,
                    namespace,
                    modelName,
                    null
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to get working memory", e);
        }
    }

    public List<String> listSessions() {
        try {
            SessionListResponse response = client.workingMemory().listSessions();
            return response != null && response.getSessions() != null ? response.getSessions() : List.of();
        } catch (Exception e) {
            throw new RuntimeException("Failed to list working-memory sessions", e);
        }
    }

    public WorkingMemoryResponse appendMessagesToWorkingMemory(
            String sessionId,
            List<MemoryMessage> messages,
            String userId,
            String modelName
    ) {
        try {
            return client.workingMemory().appendMessagesToWorkingMemory(
                    sessionId,
                    messages,
                    namespace,
                    modelName,
                    null,
                    userId
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to append working-memory messages", e);
        }
    }

    public WorkingMemoryResponse putWorkingMemory(
            String sessionId,
            WorkingMemory memory,
            String userId,
            String modelName
    ) {
        try {
            return client.workingMemory().putWorkingMemory(
                    sessionId,
                    memory,
                    userId,
                    namespace,
                    modelName,
                    null
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to put working memory", e);
        }
    }

    public AckResponse deleteWorkingMemory(String sessionId, String userId) {
        try {
            return client.workingMemory().deleteWorkingMemory(sessionId, userId, namespace);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete working memory", e);
        }
    }

    public MemoryRecordResults searchLongTermMemory(String text, String userId, int limit) {
        try {
            SearchRequest request = SearchRequest.builder()
                    .text(text)
                    .userId(userId)
                    .limit(limit)
                    .build();
            return client.longTermMemory().searchLongTermMemories(request);
        } catch (Exception e) {
            throw new RuntimeException("Failed to search long-term memory", e);
        }
    }

    public HealthCheckResponse healthCheck() {
        try {
            return client.health().healthCheck();
        } catch (Exception e) {
            throw new RuntimeException("Health check failed", e);
        }
    }

    public static MemoryMessage createMessage(String role, String content) {
        return MemoryMessage.builder()
                .role(role)
                .content(content)
                .build();
    }

    public static MemoryRecord createMemoryRecord(String text, String sessionId, String userId) {
        return MemoryRecord.builder()
                .text(text)
                .sessionId(sessionId)
                .userId(userId)
                .memoryType(MemoryType.SEMANTIC)
                .build();
    }

    public String namespace() {
        return namespace;
    }

    public MemoryAPIClient client() {
        return client;
    }
}
