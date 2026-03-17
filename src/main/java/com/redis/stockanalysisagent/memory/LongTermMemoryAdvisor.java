package com.redis.stockanalysisagent.memory;

import com.redis.agentmemory.models.longtermemory.MemoryRecordResult;
import com.redis.agentmemory.models.longtermemory.MemoryRecordResults;
import com.redis.stockanalysisagent.memory.service.AgentMemoryService;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring AI advisor that retrieves relevant long-term memories from Agent Memory Server
 * and injects them into the system prompt before the model call.
 */
public class LongTermMemoryAdvisor implements BaseAdvisor {

    public static final String RETRIEVED_MEMORIES = "long_term_memory_retrieved";
    private static final int DEFAULT_ORDER = 100;

    private final AgentMemoryService agentMemoryService;
    private final AmsChatMemoryRepository memoryRepository;
    private final int maxMemories;
    private final int order;

    public LongTermMemoryAdvisor(AgentMemoryService agentMemoryService) {
        this(agentMemoryService, null, 5, DEFAULT_ORDER);
    }

    public LongTermMemoryAdvisor(AgentMemoryService agentMemoryService, AmsChatMemoryRepository memoryRepository) {
        this(agentMemoryService, memoryRepository, 5, DEFAULT_ORDER);
    }

    public LongTermMemoryAdvisor(
            AgentMemoryService agentMemoryService,
            AmsChatMemoryRepository memoryRepository,
            int maxMemories,
            int order
    ) {
        this.agentMemoryService = agentMemoryService;
        this.memoryRepository = memoryRepository;
        this.maxMemories = maxMemories;
        this.order = order;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain advisorChain) {
        String conversationId = (String) request.context().get(ChatMemory.CONVERSATION_ID);
        String userId = parseUserId(conversationId);

        if (userId == null || userId.equals("anonymous")) {
            return request;
        }

        String userMessage = request.prompt().getUserMessage().getText();
        if (userMessage == null || userMessage.isBlank()) {
            return request;
        }

        List<String> memories = searchMemories(userMessage, userId);
        if (memoryRepository != null) {
            memoryRepository.setLastRetrievedMemories(memories);
        }

        if (memories.isEmpty()) {
            return request;
        }

        StringBuilder memoryContext = new StringBuilder();
        memoryContext.append("\n\n--- Long-Term Memories About This User ---\n");
        for (String memory : memories) {
            memoryContext.append("- ").append(memory).append("\n");
        }
        memoryContext.append("--- Use these memories to personalize your response ---\n");

        return request.mutate()
                .prompt(request.prompt().augmentSystemMessage(memoryContext.toString()))
                .context(RETRIEVED_MEMORIES, memories)
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain advisorChain) {
        return response;
    }

    private String parseUserId(String conversationId) {
        if (conversationId == null) {
            return null;
        }
        int idx = conversationId.indexOf(AmsChatMemoryRepository.SEPARATOR);
        return idx > 0 ? conversationId.substring(0, idx) : null;
    }

    private List<String> searchMemories(String query, String userId) {
        List<String> results = new ArrayList<>();
        try {
            MemoryRecordResults response = agentMemoryService.searchLongTermMemory(query, userId, maxMemories);
            if (response != null && response.getMemories() != null) {
                for (MemoryRecordResult memory : response.getMemories()) {
                    if (memory.getText() != null) {
                        results.add(memory.getText());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return results;
    }

    public static Builder builder(AgentMemoryService agentMemoryService) {
        return new Builder(agentMemoryService);
    }

    public static class Builder {
        private final AgentMemoryService agentMemoryService;
        private AmsChatMemoryRepository memoryRepository;
        private int maxMemories = 5;
        private int order = DEFAULT_ORDER;

        public Builder(AgentMemoryService agentMemoryService) {
            this.agentMemoryService = agentMemoryService;
        }

        public Builder memoryRepository(AmsChatMemoryRepository memoryRepository) {
            this.memoryRepository = memoryRepository;
            return this;
        }

        public Builder maxMemories(int maxMemories) {
            this.maxMemories = maxMemories;
            return this;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public LongTermMemoryAdvisor build() {
            return new LongTermMemoryAdvisor(agentMemoryService, memoryRepository, maxMemories, order);
        }
    }
}
