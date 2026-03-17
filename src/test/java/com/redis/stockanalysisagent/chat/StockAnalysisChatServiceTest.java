package com.redis.stockanalysisagent.chat;

import com.redis.stockanalysisagent.memory.AmsChatMemoryRepository;
import com.redis.stockanalysisagent.memory.LongTermMemoryAdvisor;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockAnalysisChatServiceTest {

    @Test
    void fallsBackToToolHandlingWhenNoChatModelIsConfigured() {
        ChatMemory chatMemory = mock(ChatMemory.class);
        AmsChatMemoryRepository memoryRepository = mock(AmsChatMemoryRepository.class);
        LongTermMemoryAdvisor longTermMemoryAdvisor = mock(LongTermMemoryAdvisor.class);
        StockAnalysisChatTools chatTools = mock(StockAnalysisChatTools.class);

        when(chatTools.analyzeStockRequest("What is Apple's current price?"))
                .thenReturn("Apple is trading at $200.00.");
        when(chatTools.consumeInvocationMetadata())
                .thenReturn(new StockAnalysisChatTools.ToolResultMetadata(true));
        when(memoryRepository.getLastRetrievedMemories())
                .thenReturn(List.of("The user asked about Apple earlier."));

        StockAnalysisChatService service = new StockAnalysisChatService(
                chatMemory,
                memoryRepository,
                longTermMemoryAdvisor,
                chatTools,
                Optional.empty()
        );

        StockAnalysisChatService.ChatTurn turn = service.chat(
                "test-user",
                "test-session",
                "What is Apple's current price?"
        );

        assertThat(turn.conversationId()).isEqualTo("test-user:test-session");
        assertThat(turn.response()).isEqualTo("Apple is trading at $200.00.");
        assertThat(turn.retrievedMemories()).containsExactly("The user asked about Apple earlier.");
        assertThat(turn.fromSemanticCache()).isTrue();
        verify(chatMemory).add(
                eq("test-user:test-session"),
                argThat((Message message) -> "What is Apple's current price?".equals(message.getText()))
        );
        verify(chatMemory).add(
                eq("test-user:test-session"),
                argThat((Message message) -> "Apple is trading at $200.00.".equals(message.getText()))
        );
    }

    @Test
    void clearsTheComputedConversationId() {
        ChatMemory chatMemory = mock(ChatMemory.class);
        AmsChatMemoryRepository memoryRepository = mock(AmsChatMemoryRepository.class);
        LongTermMemoryAdvisor longTermMemoryAdvisor = mock(LongTermMemoryAdvisor.class);
        StockAnalysisChatTools chatTools = mock(StockAnalysisChatTools.class);

        StockAnalysisChatService service = new StockAnalysisChatService(
                chatMemory,
                memoryRepository,
                longTermMemoryAdvisor,
                chatTools,
                Optional.empty()
        );

        service.clearSession("workshop-user", "session-123");

        verify(chatMemory).clear("workshop-user:session-123");
    }
}
