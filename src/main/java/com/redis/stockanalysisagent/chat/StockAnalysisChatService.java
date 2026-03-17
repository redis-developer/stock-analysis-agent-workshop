package com.redis.stockanalysisagent.chat;

import com.redis.stockanalysisagent.memory.AmsChatMemoryRepository;
import com.redis.stockanalysisagent.memory.service.AgentMemoryService;
import com.redis.agentmemory.models.longtermemory.MemoryRecordResult;
import com.redis.agentmemory.models.longtermemory.MemoryRecordResults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class StockAnalysisChatService {

    private static final Logger log = LoggerFactory.getLogger(StockAnalysisChatService.class);
    private static final int MAX_LONG_TERM_MEMORIES = 5;
    private static final int MAX_RECENT_MESSAGES = 6;

    private final ChatMemory chatMemory;
    private final AmsChatMemoryRepository memoryRepository;
    private final AgentMemoryService agentMemoryService;
    private final StockAnalysisChatTools stockAnalysisChatTools;

    public StockAnalysisChatService(
            ChatMemory chatMemory,
            AmsChatMemoryRepository memoryRepository,
            AgentMemoryService agentMemoryService,
            StockAnalysisChatTools stockAnalysisChatTools
    ) {
        this.chatMemory = chatMemory;
        this.memoryRepository = memoryRepository;
        this.agentMemoryService = agentMemoryService;
        this.stockAnalysisChatTools = stockAnalysisChatTools;
    }

    public ChatTurn chat(String userId, String sessionId, String message) {
        String conversationId = AmsChatMemoryRepository.createConversationId(userId, sessionId);
        String normalizedMessage = message == null ? "" : message.trim();

        List<String> retrievedMemories = searchLongTermMemories(normalizedMessage, userId);
        memoryRepository.setLastRetrievedMemories(retrievedMemories);

        String requestForCoordinator = buildRequestForCoordinator(
                conversationId,
                normalizedMessage,
                recentConversationContext(conversationId),
                retrievedMemories
        );

        stockAnalysisChatTools.resetInvocationMetadata();
        String response = stockAnalysisChatTools.analyzeStockRequest(requestForCoordinator);
        StockAnalysisChatTools.ToolResultMetadata metadata = stockAnalysisChatTools.consumeInvocationMetadata();

        saveTurn(conversationId, normalizedMessage, response);

        return new ChatTurn(
                conversationId,
                response,
                retrievedMemories,
                metadata.fromSemanticCache(),
                metadata.triggeredAgents()
        );
    }

    public void clearSession(String userId, String sessionId) {
        String conversationId = AmsChatMemoryRepository.createConversationId(userId, sessionId);
        try {
            chatMemory.clear(conversationId);
        } catch (RuntimeException ignored) {
        }
    }

    private void saveTurn(String conversationId, String message, String response) {
        try {
            chatMemory.add(conversationId, new UserMessage(message));
            chatMemory.add(conversationId, new AssistantMessage(response));
        } catch (RuntimeException ex) {
            log.warn("Skipping working-memory save because chat persistence failed.", ex);
        }
    }

    private List<String> recentConversationContext(String conversationId) {
        try {
            List<Message> messages = chatMemory.get(conversationId);
            if (messages == null || messages.isEmpty()) {
                return List.of();
            }

            List<String> formattedMessages = messages.stream()
                    .filter(message -> message.getMessageType() == org.springframework.ai.chat.messages.MessageType.USER
                            || message.getMessageType() == org.springframework.ai.chat.messages.MessageType.ASSISTANT)
                    .map(this::formatConversationMessage)
                    .filter(line -> line != null && !line.isBlank())
                    .toList();

            if (formattedMessages.size() <= MAX_RECENT_MESSAGES) {
                return formattedMessages;
            }

            return formattedMessages.subList(formattedMessages.size() - MAX_RECENT_MESSAGES, formattedMessages.size());
        } catch (RuntimeException ex) {
            log.warn("Skipping recent conversation lookup because chat memory retrieval failed.", ex);
            return List.of();
        }
    }

    private List<String> searchLongTermMemories(String query, String userId) {
        if (query == null || query.isBlank() || userId == null || userId.isBlank()) {
            return List.of();
        }

        try {
            MemoryRecordResults response = agentMemoryService.searchLongTermMemory(query, userId, MAX_LONG_TERM_MEMORIES);
            if (response == null || response.getMemories() == null) {
                return List.of();
            }

            return response.getMemories().stream()
                    .map(MemoryRecordResult::getText)
                    .filter(text -> text != null && !text.isBlank())
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("Skipping long-term memory lookup because retrieval failed.", ex);
            return List.of();
        }
    }

    private String buildRequestForCoordinator(
            String conversationId,
            String message,
            List<String> recentConversation,
            List<String> retrievedMemories
    ) {
        if (recentConversation.isEmpty() && retrievedMemories.isEmpty()) {
            return message;
        }

        StringBuilder request = new StringBuilder();
        request.append("Conversation ID: ").append(conversationId).append(System.lineSeparator());

        if (!recentConversation.isEmpty()) {
            request.append("Recent conversation context:")
                    .append(System.lineSeparator())
                    .append(recentConversation.stream().collect(Collectors.joining(System.lineSeparator())))
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());
        }

        if (!retrievedMemories.isEmpty()) {
            request.append("Relevant long-term memories:")
                    .append(System.lineSeparator())
                    .append(retrievedMemories.stream()
                            .map(memory -> "- " + memory)
                            .collect(Collectors.joining(System.lineSeparator())))
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());
        }

        request.append("Current user request:")
                .append(System.lineSeparator())
                .append(message);

        return request.toString();
    }

    private String formatConversationMessage(Message message) {
        String text = message.getText();
        if (text == null || text.isBlank()) {
            return null;
        }

        return switch (message.getMessageType()) {
            case USER -> "User: " + text;
            case ASSISTANT -> "Assistant: " + text;
            default -> null;
        };
    }

    public record ChatTurn(
            String conversationId,
            String response,
            List<String> retrievedMemories,
            boolean fromSemanticCache,
            List<ChatExecutionStep> triggeredAgents
    ) {
    }
}
