package com.redis.stockanalysisagent.memory;

import com.redis.agentmemory.MemoryAPIClient;
import com.redis.agentmemory.exceptions.MemoryClientException;
import com.redis.agentmemory.models.workingmemory.MemoryMessage;
import com.redis.agentmemory.models.workingmemory.WorkingMemory;
import com.redis.agentmemory.models.workingmemory.WorkingMemoryResponse;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring AI ChatMemoryRepository implementation backed by Agent Memory Server.
 *
 * Conversation ids are stored as "userId:sessionId" so working memory and
 * long-term memory can both retain user and session context.
 */
public class AmsChatMemoryRepository implements ChatMemoryRepository {

    public static final String SEPARATOR = ":";
    private static final String DEFAULT_NAMESPACE = "stock-analysis";
    private static final int DEFAULT_TTL_SECONDS = 1800;
    private static final String CONTEXT_MODEL = "gpt-4o";
    private static final Logger log = LoggerFactory.getLogger(AmsChatMemoryRepository.class);

    private final MemoryAPIClient client;
    private final String namespace;
    private volatile List<String> lastRetrievedMemories = List.of();

    public AmsChatMemoryRepository(MemoryAPIClient client) {
        this(client, DEFAULT_NAMESPACE);
    }

    public AmsChatMemoryRepository(MemoryAPIClient client, String namespace) {
        this.client = client;
        this.namespace = namespace;
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

    public Double getContextPercentage(String conversationId) {
        try {
            WorkingMemoryResponse response = client.workingMemory().getWorkingMemory(
                    parseSessionId(conversationId),
                    parseUserId(conversationId),
                    namespace,
                    CONTEXT_MODEL,
                    null
            );
            return response != null ? response.getContextPercentageTotalUsed() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public List<String> findConversationIds() {
        try {
            return client.workingMemory().listSessions().getSessions();
        } catch (MemoryClientException e) {
            return List.of();
        }
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        try {
            WorkingMemoryResponse response = client.workingMemory().getWorkingMemory(
                    parseSessionId(conversationId),
                    parseUserId(conversationId),
                    namespace,
                    null,
                    null
            );
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
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        String userId = parseUserId(conversationId);
        String sessionId = parseSessionId(conversationId);

        try {
            List<MemoryMessage> existingMessages = new ArrayList<>();
            try {
                WorkingMemoryResponse existing = client.workingMemory().getWorkingMemory(
                        sessionId,
                        userId,
                        namespace,
                        null,
                        null
                );
                if (existing != null && existing.getMessages() != null) {
                    existingMessages.addAll(existing.getMessages());
                }
            } catch (Exception ignored) {
            }

            List<MemoryMessage> newMessages = new ArrayList<>();
            for (Message msg : messages) {
                MemoryMessage amsMsg = convertToAmsMessage(msg);
                if (amsMsg != null && !isDuplicate(amsMsg, existingMessages)) {
                    newMessages.add(amsMsg);
                }
            }

            if (newMessages.isEmpty()) {
                return;
            }

            boolean firstMessage = existingMessages.isEmpty();
            client.workingMemory().appendMessagesToWorkingMemory(
                    sessionId,
                    newMessages,
                    namespace,
                    CONTEXT_MODEL,
                    null,
                    userId
            );

            if (firstMessage) {
                try {
                    WorkingMemoryResponse current = client.workingMemory().getWorkingMemory(
                            sessionId,
                            userId,
                            namespace,
                            null,
                            null
                    );
                    if (current != null && current.getMessages() != null) {
                        WorkingMemory withTtl = WorkingMemory.builder()
                                .namespace(namespace)
                                .sessionId(sessionId)
                                .messages(current.getMessages())
                                .userId(userId)
                                .ttlSeconds(DEFAULT_TTL_SECONDS)
                                .build();
                        client.workingMemory().putWorkingMemory(
                                sessionId,
                                withTtl,
                                userId,
                                namespace,
                                CONTEXT_MODEL,
                                null
                        );
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            log.warn("Skipping Agent Memory Server write for conversation {} because working-memory persistence failed.", conversationId, e);
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        try {
            client.workingMemory().deleteWorkingMemory(
                    parseSessionId(conversationId),
                    parseUserId(conversationId),
                    namespace
            );
        } catch (Exception ignored) {
        }
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
}
