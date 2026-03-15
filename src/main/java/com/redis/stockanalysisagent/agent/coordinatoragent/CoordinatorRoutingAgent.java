package com.redis.stockanalysisagent.agent.coordinatoragent;

import com.redis.stockanalysisagent.api.AnalysisRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class CoordinatorRoutingAgent {

    private static final String DEFAULT_PROMPT = """
            ROLE
            You are the Coordinator Routing Agent for a stock-analysis system.

            RESPONSIBILITY
            Decide whether the request is in scope, whether you need more information, and which specialized agents should run.

            AVAILABLE AGENTS
            - MARKET_DATA: quote, recent price movement, basic price context
            - FUNDAMENTALS: financial health, valuation, earnings, margins, revenue trends
            - NEWS: recent events, headlines, macro or company-specific developments
            - TECHNICAL_ANALYSIS: trend, momentum, RSI, support, resistance, chart-based signals

            INPUT HANDLING
            - The user may provide a complete stock-analysis request, an incomplete request, or an unsupported request.
            - If the request is missing information required to proceed, return finishReason = NEEDS_MORE_INPUT.
            - Use nextPrompt for one short, specific follow-up question.
            - If the request is outside the capabilities of this stock-analysis workshop, return finishReason = OUT_OF_SCOPE.
            - If the request cannot be fulfilled even after clarification, return finishReason = CANNOT_PROCEED.
            - Return finishReason = COMPLETED only when you have enough information to route the work.

            COMPLETED RULES
            - When finishReason = COMPLETED, set resolvedTicker to the stock ticker in uppercase.
            - When finishReason = COMPLETED, set resolvedQuestion to the user's final stock-analysis question.
            - Select the smallest set of specialized agents needed to answer the question well.
            - Do not include SYNTHESIS in selectedAgents. The application adds it when needed.
            - Set requiresSynthesis to true when the final answer should combine multiple signals or needs a narrative explanation.
            - Set requiresSynthesis to false when a direct single-agent answer is enough.
            - Prefer minimal routing over broad routing.
            - Return only agent names from the allowed enum values.

            CLARIFICATION GUIDANCE
            - Ask for a ticker when a company-specific request does not identify one clearly.
            - Ask for the missing analysis goal when the user provides only a ticker.
            - If the user names a company instead of a ticker and the mapping is unambiguous, you may resolve it.

            OUTPUT
            Return valid JSON that matches the requested schema.
            """;

    private final ChatClient coordinatorChatClient;

    public CoordinatorRoutingAgent(Optional<ChatModel> chatModel) {
        if (chatModel.isEmpty()) {
            this.coordinatorChatClient = null;
            return;
        }

        ChatMemory chatMemory = MessageWindowChatMemory.builder().build();
        this.coordinatorChatClient = ChatClient.builder(chatModel.orElseThrow())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .defaultSystem(DEFAULT_PROMPT)
                .build();
    }

    public RoutingDecision route(AnalysisRequest request) {
        return route("""
                Ticker: %s
                Question: %s
                """.formatted(request.ticker(), request.question()));
    }

    public RoutingDecision route(String userMessage) {
        return route(userMessage, UUID.randomUUID().toString());
    }

    public RoutingDecision route(String userMessage, String conversationId) {
        if (coordinatorChatClient == null) {
            throw new IllegalStateException("""
                    No chat model is configured for LLM-based coordinator routing.
                    Configure a Spring AI chat model for runtime use, or use the automated tests for workshop validation.
                    """.stripIndent().trim());
        }

        ResponseEntity<ChatResponse, RoutingDecision> response = coordinatorChatClient
                .prompt()
                .user(userMessage)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .responseEntity(RoutingDecision.class);

        RoutingDecision decision = response.entity();
        if (decision == null) {
            throw new IllegalStateException("Coordinator routing returned an empty response.");
        }

        return decision;
    }
}
