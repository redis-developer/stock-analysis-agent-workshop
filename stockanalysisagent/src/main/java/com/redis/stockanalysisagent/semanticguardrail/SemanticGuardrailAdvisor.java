package com.redis.stockanalysisagent.semanticguardrail;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import tools.jackson.core.io.JsonStringEncoder;

import java.util.List;

public class SemanticGuardrailAdvisor implements CallAdvisor {

    public static final String GUARDRAIL_BLOCKED = "semantic_guardrail_blocked";
    public static final String GUARDRAIL_ROUTE = "semantic_guardrail_route";
    private static final int DEFAULT_ORDER = 25;
    private static final JsonStringEncoder JSON_STRING_ENCODER = JsonStringEncoder.getInstance();

    private final SemanticGuardrailService semanticGuardrailService;

    public SemanticGuardrailAdvisor(SemanticGuardrailService semanticGuardrailService) {
        this.semanticGuardrailService = semanticGuardrailService;
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return DEFAULT_ORDER;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String userMessage = userMessage(request);
        var match = semanticGuardrailService.match(userMessage);
        if (match.isEmpty()) {
            return chain.nextCall(request);
        }

        String routeName = match.get().routeName();
        return ChatClientResponse.builder()
                .chatResponse(toChatResponse(blockedResponse(routeName), routeName))
                .context(request.context())
                .context(GUARDRAIL_BLOCKED, true)
                .context(GUARDRAIL_ROUTE, routeName)
                .build();
    }

    private String userMessage(ChatClientRequest request) {
        if (request == null || request.prompt() == null || request.prompt().getUserMessage() == null) {
            return null;
        }
        return request.prompt().getUserMessage().getText();
    }

    private ChatResponse toChatResponse(String finalResponse, String routeName) {
        return ChatResponse.builder()
                .metadata(ChatResponseMetadata.builder()
                        .keyValue(GUARDRAIL_BLOCKED, true)
                        .keyValue(GUARDRAIL_ROUTE, routeName)
                        .build())
                .generations(List.of(new Generation(new AssistantMessage(toCoordinatorPayload(finalResponse)))))
                .build();
    }

    private String blockedResponse(String routeName) {
        return switch (routeName) {
            case SemanticGuardrailService.ALIEN_JOKES_ROUTE ->
                    "I can help with stock analysis, but I can't help with alien jokes.";
            case SemanticGuardrailService.CORPORATE_AGILE_ROUTE ->
                    "I can help with stock analysis, but I can't help with corporate agile questions.";
            default -> "I can help with stock analysis, but I can't help with that request.";
        };
    }

    private String toCoordinatorPayload(String finalResponse) {
        StringBuilder escapedFinalResponse = new StringBuilder();
        JSON_STRING_ENCODER.quoteAsString(finalResponse == null ? "" : finalResponse, escapedFinalResponse);
        return "{\"finishReason\":\"DIRECT_RESPONSE\",\"finalResponse\":\"%s\"}".formatted(escapedFinalResponse);
    }
}
