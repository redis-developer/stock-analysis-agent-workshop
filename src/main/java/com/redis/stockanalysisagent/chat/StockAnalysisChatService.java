package com.redis.stockanalysisagent.chat;

import com.redis.stockanalysisagent.agent.AgentExecution;
import com.redis.stockanalysisagent.memory.AmsChatMemoryRepository;
import com.redis.stockanalysisagent.memory.LongTermMemoryAdvisor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class StockAnalysisChatService {

    private static final Logger log = LoggerFactory.getLogger(StockAnalysisChatService.class);

    private static final String SYSTEM_PROMPT = """
            You are a conversational stock-analysis assistant backed by a multi-agent analysis system.

            RULES
            - Use the available stock-analysis tool for questions about stocks, companies, prices, fundamentals, news, technicals, or combined investment analysis.
            - Use conversation memory to resolve follow-up references like "it", "the same company", or "what about technicals?".
            - If the tool returns a clarification question, ask it directly and briefly.
            - If the tool returns an analysis, present it clearly and conversationally without inventing extra facts.
            - If the user asks something outside stock analysis, answer briefly that your scope is stock analysis.
            - Never invent market data, financials, headlines, or technical indicators.
            """;

    private final ChatMemory chatMemory;
    private final AmsChatMemoryRepository memoryRepository;
    private final StockAnalysisChatTools stockAnalysisChatTools;
    private final ChatClient chatClient;

    public StockAnalysisChatService(
            ChatMemory chatMemory,
            AmsChatMemoryRepository memoryRepository,
            LongTermMemoryAdvisor longTermMemoryAdvisor,
            StockAnalysisChatTools stockAnalysisChatTools,
            Optional<ChatModel> chatModel
    ) {
        this.chatMemory = chatMemory;
        this.memoryRepository = memoryRepository;
        this.stockAnalysisChatTools = stockAnalysisChatTools;

        if (chatModel.isEmpty()) {
            this.chatClient = null;
            return;
        }

        this.chatClient = ChatClient.builder(chatModel.orElseThrow())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultAdvisors(longTermMemoryAdvisor)
                .defaultTools(stockAnalysisChatTools)
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }

    public ChatTurn chat(String userId, String sessionId, String message) {
        String conversationId = AmsChatMemoryRepository.createConversationId(userId, sessionId);
        stockAnalysisChatTools.resetInvocationMetadata();

        if (chatClient == null) {
            return fallbackTurn(conversationId, message);
        }

        try {
            String response = chatClient.prompt()
                    .user(message)
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();
            StockAnalysisChatTools.ToolResultMetadata metadata = stockAnalysisChatTools.consumeInvocationMetadata();

            return new ChatTurn(
                    conversationId,
                    response,
                    memoryRepository.getLastRetrievedMemories(),
                    metadata.fromSemanticCache(),
                    metadata.triggeredAgents()
            );
        } catch (RuntimeException ex) {
            log.warn("Falling back to deterministic chat handling because the memory-backed chat client failed.", ex);
            return fallbackTurn(conversationId, message);
        }
    }

    public void clearSession(String userId, String sessionId) {
        String conversationId = AmsChatMemoryRepository.createConversationId(userId, sessionId);
        try {
            chatMemory.clear(conversationId);
        } catch (RuntimeException ignored) {
        }
    }

    private ChatTurn fallbackTurn(String conversationId, String message) {
        stockAnalysisChatTools.resetInvocationMetadata();
        String response = stockAnalysisChatTools.analyzeStockRequest(message);
        StockAnalysisChatTools.ToolResultMetadata metadata = stockAnalysisChatTools.consumeInvocationMetadata();

        try {
            chatMemory.add(conversationId, new UserMessage(message));
            chatMemory.add(conversationId, new AssistantMessage(response));
        } catch (RuntimeException ignored) {
        }

        return new ChatTurn(
                conversationId,
                response,
                memoryRepository.getLastRetrievedMemories(),
                metadata.fromSemanticCache(),
                metadata.triggeredAgents()
        );
    }

    public record ChatTurn(
            String conversationId,
            String response,
            List<String> retrievedMemories,
            boolean fromSemanticCache,
            List<AgentExecution> triggeredAgents
    ) {
    }
}
