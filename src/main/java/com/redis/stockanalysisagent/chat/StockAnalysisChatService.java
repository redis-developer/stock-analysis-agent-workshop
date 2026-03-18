package com.redis.stockanalysisagent.chat;

import com.redis.stockanalysisagent.memory.AmsChatMemoryRepository;
import com.redis.stockanalysisagent.semanticcache.SemanticAnalysisCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class StockAnalysisChatService {

    private static final Logger log = LoggerFactory.getLogger(StockAnalysisChatService.class);
    private static final String KIND_SYSTEM = "system";
    private static final String SEMANTIC_CACHE = "SEMANTIC_CACHE";

    private final ChatMemory chatMemory;
    private final AmsChatMemoryRepository memoryRepository;
    private final ChatAnalysisRunner chatAnalysisRunner;
    private final SemanticAnalysisCache semanticAnalysisCache;

    public StockAnalysisChatService(
            ChatMemory chatMemory,
            AmsChatMemoryRepository memoryRepository,
            ChatAnalysisRunner chatAnalysisRunner,
            SemanticAnalysisCache semanticAnalysisCache
    ) {
        this.chatMemory = chatMemory;
        this.memoryRepository = memoryRepository;
        this.chatAnalysisRunner = chatAnalysisRunner;
        this.semanticAnalysisCache = semanticAnalysisCache;
    }

    public ChatTurn chat(String userId, String sessionId, String message) {
        String conversationId = AmsChatMemoryRepository.createConversationId(userId, sessionId);
        String normalizedMessage = message == null ? "" : message.trim();
        List<ChatExecutionStep> executionSteps = new ArrayList<>();

        long semanticCacheStartedAt = System.nanoTime();
        java.util.Optional<String> cachedResponse = semanticAnalysisCache.findAnswer(normalizedMessage);
        long semanticCacheDurationMs = elapsedDurationMs(semanticCacheStartedAt);
        if (cachedResponse.isPresent()) {
            memoryRepository.setLastRetrievedMemories(List.of());
            return new ChatTurn(
                    conversationId,
                    cachedResponse.get(),
                    List.of(),
                    true,
                    List.of(systemStep(
                            SEMANTIC_CACHE,
                            "Semantic cache",
                            semanticCacheDurationMs,
                            "Found a reusable response in the semantic cache and returned it directly."
                    ))
            );
        }
        executionSteps.add(systemStep(
                SEMANTIC_CACHE,
                "Semantic cache",
                semanticCacheDurationMs,
                "Checked the semantic cache and found no reusable response."
        ));

        ChatAnalysisRunner.AnalysisTurn analysisTurn = chatAnalysisRunner.analyze(normalizedMessage, conversationId);
        executionSteps.addAll(analysisTurn.executionSteps());
        if (analysisTurn.cacheable()) {
            semanticAnalysisCache.store(normalizedMessage, analysisTurn.response());
        }

        long saveTurnStartedAt = System.nanoTime();
        boolean saveSucceeded = saveTurn(conversationId, normalizedMessage, analysisTurn.response());
        executionSteps.add(systemStep(
                "TURN_SAVE",
                "Turn save",
                elapsedDurationMs(saveTurnStartedAt),
                turnSaveSummary(saveSucceeded)
        ));

        return new ChatTurn(
                conversationId,
                analysisTurn.response(),
                memoryRepository.getLastRetrievedMemories(),
                false,
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

    private ChatExecutionStep systemStep(String id, String label, long durationMs, String summary) {
        return new ChatExecutionStep(id, label, KIND_SYSTEM, durationMs, summary);
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
            List<ChatExecutionStep> executionSteps
    ) {
    }
}
