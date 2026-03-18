package com.redis.stockanalysisagent.memory;

import com.redis.agentmemory.models.workingmemory.MemoryMessage;
import com.redis.agentmemory.models.workingmemory.WorkingMemory;
import com.redis.agentmemory.models.workingmemory.WorkingMemoryResponse;
import com.redis.stockanalysisagent.memory.service.AgentMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Spring AI ChatMemoryRepository implementation backed by Agent Memory Server.
 *
 * Conversation ids are stored as "userId:sessionId" so working memory and
 * long-term memory can both retain user and session context.
 */
public class AmsChatMemoryRepository implements ChatMemoryRepository {

    public static final String SEPARATOR = ":";
    private static final int DEFAULT_TTL_SECONDS = 1800;
    private static final String MEMORY_MODEL = "gpt-4o";
    private static final Logger log = LoggerFactory.getLogger(AmsChatMemoryRepository.class);

    private final AgentMemoryService agentMemoryService;
    private volatile List<String> lastRetrievedMemories = List.of();

    public AmsChatMemoryRepository(AgentMemoryService agentMemoryService) {
        this.agentMemoryService = agentMemoryService;
    }

    public static String createConversationId(String userId, String sessionId) {
        return userId + SEPARATOR + sessionId;
    }

    public void setLastRetrievedMemories(List<String> memories) {
        this.lastRetrievedMemories = memories != null ? memories : List.of();
    }

    public List<String> getLastRetrievedMemories() {
        return lastRetrievedMemories;
    }

    @Override
    public List<String> findConversationIds() {
        return callOrDefault("list memory sessions", agentMemoryService::listSessions, List.of());
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        WorkingMemoryResponse response = loadWorkingMemory(conversationId);
        if (response == null || response.getMessages() == null) {
            return List.of();
        }

        List<Message> messages = new ArrayList<>();
        for (MemoryMessage msg : response.getMessages()) {
            Message springMessage = convertToSpringMessage(msg);
            if (springMessage != null) {
                messages.add(springMessage);
            }
        }
        return messages;
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        String userId = parseUserId(conversationId);
        String sessionId = parseSessionId(conversationId);
        List<MemoryMessage> existingMessages = existingMessages(conversationId);
        List<MemoryMessage> newMessages = toNewMessages(messages, existingMessages);

        if (newMessages.isEmpty()) {
            return;
        }

        runSafely("save working memory for conversation " + conversationId, () -> {
            boolean firstMessage = existingMessages.isEmpty();
            agentMemoryService.appendMessagesToWorkingMemory(
                    sessionId,
                    newMessages,
                    userId,
                    MEMORY_MODEL
            );

            if (firstMessage) {
                applyTtl(sessionId, userId);
            }
        });
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        runSafely("delete working memory for conversation " + conversationId, () -> {
            agentMemoryService.deleteWorkingMemory(
                    parseSessionId(conversationId),
                    parseUserId(conversationId)
            );
        });
    }

    private String parseSessionId(String conversationId) {
        if (conversationId == null) {
            return "default";
        }
        int idx = conversationId.indexOf(SEPARATOR);
        return idx > 0 ? conversationId.substring(idx + 1) : conversationId;
    }

    private String parseUserId(String conversationId) {
        if (conversationId == null) {
            return null;
        }
        int idx = conversationId.indexOf(SEPARATOR);
        return idx > 0 ? conversationId.substring(0, idx) : null;
    }

    private boolean isDuplicate(MemoryMessage newMsg, List<MemoryMessage> existing) {
        return existing.stream().anyMatch(m ->
                m.getRole().equals(newMsg.getRole())
                        && m.getContent().equals(newMsg.getContent())
        );
    }

    private Message convertToSpringMessage(MemoryMessage msg) {
        if (msg == null || msg.getRole() == null) {
            return null;
        }

        String content = msg.getContent() != null ? msg.getContent() : "";

        return switch (msg.getRole()) {
            case "user" -> new UserMessage(content);
            case "assistant" -> new AssistantMessage(content);
            case "system" -> new SystemMessage(content);
            default -> null;
        };
    }

    private MemoryMessage convertToAmsMessage(Message msg) {
        if (msg == null) {
            return null;
        }

        String role = switch (msg.getMessageType()) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case SYSTEM -> "system";
            default -> null;
        };

        if (role == null) {
            return null;
        }

        return MemoryMessage.builder()
                .role(role)
                .content(msg.getText())
                .build();
    }

    private WorkingMemoryResponse loadWorkingMemory(String conversationId) {
        return callOrDefault(
                "load working memory for conversation " + conversationId,
                () -> agentMemoryService.getWorkingMemory(
                        parseSessionId(conversationId),
                        parseUserId(conversationId),
                        null
                ),
                null
        );
    }

    private List<MemoryMessage> existingMessages(String conversationId) {
        WorkingMemoryResponse existing = loadWorkingMemory(conversationId);
        if (existing == null || existing.getMessages() == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(existing.getMessages());
    }

    private List<MemoryMessage> toNewMessages(List<Message> messages, List<MemoryMessage> existingMessages) {
        List<MemoryMessage> newMessages = new ArrayList<>();
        for (Message message : messages) {
            MemoryMessage amsMessage = convertToAmsMessage(message);
            if (amsMessage != null && !isDuplicate(amsMessage, existingMessages)) {
                newMessages.add(amsMessage);
            }
        }
        return newMessages;
    }

    private void applyTtl(String sessionId, String userId) {
        WorkingMemoryResponse current = agentMemoryService.getWorkingMemory(sessionId, userId, null);
        if (current == null || current.getMessages() == null) {
            return;
        }

        WorkingMemory withTtl = WorkingMemory.builder()
                .namespace(agentMemoryService.namespace())
                .sessionId(sessionId)
                .messages(current.getMessages())
                .userId(userId)
                .ttlSeconds(DEFAULT_TTL_SECONDS)
                .build();

        agentMemoryService.putWorkingMemory(
                sessionId,
                withTtl,
                userId,
                MEMORY_MODEL
        );
    }

    private void runSafely(String action, Runnable runnable) {
        try {
            runnable.run();
        } catch (RuntimeException e) {
            log.warn("Skipping Agent Memory Server action because {} failed.", action, e);
        }
    }

    private <T> T callOrDefault(String action, Supplier<T> supplier, T fallback) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            log.warn("Returning fallback because {} failed.", action, e);
            return fallback;
        }
    }
}
