package com.redis.stockanalysisagent.memory;

import com.redis.agentmemory.MemoryAPIClient;
import com.redis.agentmemory.exceptions.MemoryClientException;
import com.redis.agentmemory.services.WorkingMemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AmsChatMemoryRepositoryTest {

    @Test
    void saveAllDoesNotThrowWhenAgentMemoryServerWriteFails() throws Exception {
        MemoryAPIClient client = mock(MemoryAPIClient.class);
        WorkingMemoryService workingMemoryService = mock(WorkingMemoryService.class);
        when(client.workingMemory()).thenReturn(workingMemoryService);
        when(workingMemoryService.getWorkingMemory("session-123", "user-1", "stock-analysis", null, null))
                .thenReturn(null);
        doThrow(new MemoryClientException("connection reset"))
                .when(workingMemoryService)
                .appendMessagesToWorkingMemory(
                        eq("session-123"),
                        anyList(),
                        eq("stock-analysis"),
                        eq("gpt-4o"),
                        isNull(),
                        eq("user-1")
                );

        AmsChatMemoryRepository repository = new AmsChatMemoryRepository(client, "stock-analysis");

        assertThatCode(() -> repository.saveAll(
                "user-1:session-123",
                List.of(new UserMessage("Hello"))
        )).doesNotThrowAnyException();

        verify(workingMemoryService).appendMessagesToWorkingMemory(
                eq("session-123"),
                anyList(),
                eq("stock-analysis"),
                eq("gpt-4o"),
                isNull(),
                eq("user-1")
        );
    }
}
