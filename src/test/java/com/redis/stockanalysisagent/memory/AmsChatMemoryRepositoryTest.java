package com.redis.stockanalysisagent.memory;

import com.redis.stockanalysisagent.memory.service.AgentMemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AmsChatMemoryRepositoryTest {

    @Test
    void saveAllDoesNotThrowWhenAgentMemoryServerWriteFails() {
        AgentMemoryService agentMemoryService = mock(AgentMemoryService.class);
        when(agentMemoryService.getWorkingMemory("session-123", "user-1", null))
                .thenReturn(null);
        doThrow(new RuntimeException("connection reset"))
                .when(agentMemoryService)
                .appendMessagesToWorkingMemory(
                        eq("session-123"),
                        anyList(),
                        eq("user-1"),
                        eq("gpt-4o")
                );

        AmsChatMemoryRepository repository = new AmsChatMemoryRepository(agentMemoryService);

        assertThatCode(() -> repository.saveAll(
                "user-1:session-123",
                List.of(new UserMessage("Hello"))
        )).doesNotThrowAnyException();

        verify(agentMemoryService).appendMessagesToWorkingMemory(
                eq("session-123"),
                anyList(),
                eq("user-1"),
                eq("gpt-4o")
        );
    }
}
