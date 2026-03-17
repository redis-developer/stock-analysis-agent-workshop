package com.redis.stockanalysisagent.memory;

import com.redis.agentmemory.models.longtermemory.MemoryRecordResult;
import com.redis.agentmemory.models.longtermemory.MemoryRecordResults;
import com.redis.stockanalysisagent.memory.service.AgentMemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LongTermMemoryAdvisorTest {

    @Test
    void skipsLongTermLookupWhenTheConversationHasNoHistory() {
        AgentMemoryService agentMemoryService = mock(AgentMemoryService.class);
        AmsChatMemoryRepository memoryRepository = mock(AmsChatMemoryRepository.class);
        AdvisorChain advisorChain = mock(AdvisorChain.class);

        when(memoryRepository.findByConversationId("user-1:session-1")).thenReturn(List.of());

        LongTermMemoryAdvisor advisor = new LongTermMemoryAdvisor(agentMemoryService, memoryRepository, 5);

        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(new UserMessage("What's the current price of Apple?")))
                .context(Map.of(ChatMemory.CONVERSATION_ID, "user-1:session-1"))
                .build();

        ChatClientRequest updated = advisor.before(request, advisorChain);

        assertThat(updated.prompt().getSystemMessages()).isEmpty();
        verify(memoryRepository).setLastRetrievedMemories(List.of());
        verify(agentMemoryService, never()).searchLongTermMemory("What's the current price of Apple?", "user-1", 5);
    }

    @Test
    void injectsRetrievedMemoriesWhenConversationHistoryExists() {
        AgentMemoryService agentMemoryService = mock(AgentMemoryService.class);
        AmsChatMemoryRepository memoryRepository = mock(AmsChatMemoryRepository.class);
        AdvisorChain advisorChain = mock(AdvisorChain.class);
        MemoryRecordResults memoryResults = mock(MemoryRecordResults.class);
        MemoryRecordResult memoryRecord = mock(MemoryRecordResult.class);

        when(memoryRepository.findByConversationId("user-1:session-1"))
                .thenReturn(List.of(new UserMessage("Tell me about Apple.")));
        when(agentMemoryService.searchLongTermMemory("What about fundamentals?", "user-1", 5))
                .thenReturn(memoryResults);
        when(memoryResults.getMemories()).thenReturn(List.of(memoryRecord));
        when(memoryRecord.getText()).thenReturn("The user usually follows up on Apple after asking about price.");

        LongTermMemoryAdvisor advisor = new LongTermMemoryAdvisor(agentMemoryService, memoryRepository, 5);

        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(new UserMessage("What about fundamentals?")))
                .context(Map.of(ChatMemory.CONVERSATION_ID, "user-1:session-1"))
                .build();

        ChatClientRequest updated = advisor.before(request, advisorChain);

        assertThat(updated.context())
                .containsKey(LongTermMemoryAdvisor.RETRIEVED_MEMORIES);
        assertThat(updated.prompt().getSystemMessage().getText())
                .contains("Long-term memories about this user:")
                .contains("The user usually follows up on Apple after asking about price.");
        verify(memoryRepository).setLastRetrievedMemories(List.of());
        verify(memoryRepository).setLastRetrievedMemories(
                List.of("The user usually follows up on Apple after asking about price.")
        );
    }
}
