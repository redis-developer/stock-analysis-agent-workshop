package com.redis.stockanalysisagent.chat;

import com.redis.stockanalysisagent.agent.orchestration.TokenUsageSummary;
import com.redis.stockanalysisagent.memory.AmsChatMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final String KIND_SYSTEM = "system";

    private final ChatMemory chatMemory;
    private final AmsChatMemoryRepository memoryRepository;
    private final ChatAnalysisService chatAnalysisService;

    public ChatService(
            ChatMemory chatMemory,
            AmsChatMemoryRepository memoryRepository,
            ChatAnalysisService chatAnalysisService
    ) {
        this.chatMemory = chatMemory;
        this.memoryRepository = memoryRepository;
        this.chatAnalysisService = chatAnalysisService;
    }

    public ChatTurn chat(String userId, String sessionId, String message, Integer retrievedMemoriesLimit) {
        String conversationId = AmsChatMemoryRepository.createConversationId(userId, sessionId);
        String normalizedMessage = message == null ? "" : message.trim();
        List<ChatExecutionStep> executionSteps = new ArrayList<>();
        memoryRepository.setLastRetrievedMemories(List.of());

        ChatAnalysisService.AnalysisTurn analysisTurn = chatAnalysisService.analyze(
                normalizedMessage,
                conversationId,
                retrievedMemoriesLimit
        );
        executionSteps.addAll(analysisTurn.executionSteps());

        long saveTurnStartedAt = System.nanoTime();
        boolean saveSucceeded = saveTurn(conversationId, normalizedMessage, analysisTurn.response());
        executionSteps.add(systemStep(
                "TURN_SAVE",
                "Turn save",
                elapsedDurationMs(saveTurnStartedAt),
                turnSaveSummary(saveSucceeded),
                null
        ));

        return new ChatTurn(
                conversationId,
                analysisTurn.response(),
                memoryRepository.getLastRetrievedMemories(),
                analysisTurn.fromSemanticCache(),
                analysisTurn.fromSemanticGuardrail(),
                analysisTurn.tokenUsage(),
                List.copyOf(executionSteps)
        );
    }

    public void clearSession(String userId, String sessionId) {
        String conversationId = AmsChatMemoryRepository.createConversationId(userId, sessionId);
        try {
            chatMemory.clear(conversationId);
        } catch (RuntimeException ignored) {
        }
    }

    private boolean saveTurn(String conversationId, String message, String response) {
        try {
            chatMemory.add(conversationId, new UserMessage(message));
            chatMemory.add(conversationId, new AssistantMessage(response));
            return true;
        } catch (RuntimeException ex) {
            log.warn("Skipping working-memory save because chat persistence failed.", ex);
            return false;
        }
    }

    private ChatExecutionStep systemStep(
            String id,
            String label,
            long durationMs,
            String summary,
            TokenUsageSummary tokenUsage
    ) {
        return new ChatExecutionStep(id, label, KIND_SYSTEM, durationMs, summary, tokenUsage);
    }

    private long elapsedDurationMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private String turnSaveSummary(boolean saveSucceeded) {
        return saveSucceeded
                ? "Persisted the user message and assistant response to working memory."
                : "Skipped working-memory persistence because the save failed.";
    }

    public record ChatTurn(
            String conversationId,
            String response,
            List<String> retrievedMemories,
            boolean fromSemanticCache,
            boolean fromSemanticGuardrail,
            TokenUsageSummary tokenUsage,
            List<ChatExecutionStep> executionSteps
    ) {
    }
}
