package com.redis.stockanalysisagent.chat;

import com.redis.stockanalysisagent.memory.AmsChatMemoryRepository;
import com.redis.stockanalysisagent.memory.service.AgentMemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockAnalysisChatServiceTest {

    @Test
    void routesDirectlyThroughCoordinatorPathAndPersistsTheTurn() {
        ChatMemory chatMemory = mock(ChatMemory.class);
        AmsChatMemoryRepository memoryRepository = mock(AmsChatMemoryRepository.class);
        AgentMemoryService agentMemoryService = mock(AgentMemoryService.class);
        StockAnalysisChatTools chatTools = mock(StockAnalysisChatTools.class);

        when(chatTools.analyzeStockRequest("What is Apple's current price?"))
                .thenReturn("Apple is trading at $200.00.");
        when(chatTools.consumeInvocationMetadata())
                .thenReturn(new StockAnalysisChatTools.ToolResultMetadata(
                        true,
                        List.of(new ChatExecutionStep("MARKET_DATA", 245, "Processed the latest quote."))
                ));

        StockAnalysisChatService service = new StockAnalysisChatService(
                chatMemory,
                memoryRepository,
                agentMemoryService,
                chatTools
        );

        StockAnalysisChatService.ChatTurn turn = service.chat(
                "test-user",
                "test-session",
                "What is Apple's current price?"
        );

        assertThat(turn.conversationId()).isEqualTo("test-user:test-session");
        assertThat(turn.response()).isEqualTo("Apple is trading at $200.00.");
        assertThat(turn.retrievedMemories()).isEmpty();
        assertThat(turn.fromSemanticCache()).isTrue();
        assertThat(turn.triggeredAgents())
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.agentType()).isEqualTo("MARKET_DATA");
                    assertThat(step.durationMs()).isEqualTo(245);
                    assertThat(step.summary()).isEqualTo("Processed the latest quote.");
                });
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
    void includesRecentConversationContextBeforeCallingTheCoordinatorPath() {
        ChatMemory chatMemory = mock(ChatMemory.class);
        AmsChatMemoryRepository memoryRepository = mock(AmsChatMemoryRepository.class);
        AgentMemoryService agentMemoryService = mock(AgentMemoryService.class);
        StockAnalysisChatTools chatTools = mock(StockAnalysisChatTools.class);

        when(chatMemory.get("test-user:test-session"))
                .thenReturn(List.of(
                        new UserMessage("What's the current price of Apple?"),
                        new AssistantMessage("Apple is trading at $200.00.")
                ));
        when(chatTools.analyzeStockRequest(contains("Recent conversation context:")))
                .thenReturn("Apple's fundamentals look strong.");
        when(chatTools.consumeInvocationMetadata())
                .thenReturn(new StockAnalysisChatTools.ToolResultMetadata(false, List.of()));

        StockAnalysisChatService service = new StockAnalysisChatService(
                chatMemory,
                memoryRepository,
                agentMemoryService,
                chatTools
        );

        StockAnalysisChatService.ChatTurn turn = service.chat(
                "test-user",
                "test-session",
                "What about fundamentals?"
        );

        assertThat(turn.response()).isEqualTo("Apple's fundamentals look strong.");
        verify(chatTools).analyzeStockRequest(contains("User: What's the current price of Apple?"));
        verify(chatTools).analyzeStockRequest(contains("Assistant: Apple is trading at $200.00."));
        verify(chatTools).analyzeStockRequest(contains("Current user request:\nWhat about fundamentals?"));
    }

    @Test
    void clearsTheComputedConversationId() {
        ChatMemory chatMemory = mock(ChatMemory.class);
        AmsChatMemoryRepository memoryRepository = mock(AmsChatMemoryRepository.class);
        AgentMemoryService agentMemoryService = mock(AgentMemoryService.class);
        StockAnalysisChatTools chatTools = mock(StockAnalysisChatTools.class);

        StockAnalysisChatService service = new StockAnalysisChatService(
                chatMemory,
                memoryRepository,
                agentMemoryService,
                chatTools
        );

        service.clearSession("workshop-user", "session-123");

        verify(chatMemory).clear("workshop-user:session-123");
    }
}
