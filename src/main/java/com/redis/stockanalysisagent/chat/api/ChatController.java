package com.redis.stockanalysisagent.chat.api;

import com.redis.stockanalysisagent.chat.StockAnalysisChatService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final StockAnalysisChatService stockAnalysisChatService;
    private final String defaultUserId;

    public ChatController(
            StockAnalysisChatService stockAnalysisChatService,
            @Value("${app.chat.user-id:workshop-user}") String defaultUserId
    ) {
        this.stockAnalysisChatService = stockAnalysisChatService;
        this.defaultUserId = defaultUserId;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        String userId = normalizeUserId(request.userId());
        String sessionId = normalizeSessionId(request.sessionId());
        long startedAt = System.nanoTime();
        StockAnalysisChatService.ChatTurn turn = stockAnalysisChatService.chat(
                userId,
                sessionId,
                request.message().trim()
        );
        long responseTimeMs = (System.nanoTime() - startedAt) / 1_000_000;

        return ResponseEntity.ok(new ChatResponse(
                userId,
                sessionId,
                turn.conversationId(),
                turn.response(),
                turn.retrievedMemories(),
                turn.fromSemanticCache(),
                turn.triggeredAgents(),
                responseTimeMs
        ));
    }

    @GetMapping("/context")
    public ResponseEntity<ChatContextResponse> context() {
        return ResponseEntity.ok(new ChatContextResponse(defaultUserId));
    }

    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Void> clearSession(
            @PathVariable String sessionId,
            @RequestParam(required = false) String userId
    ) {
        stockAnalysisChatService.clearSession(normalizeUserId(userId), sessionId);
        return ResponseEntity.noContent().build();
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return UUID.randomUUID().toString();
        }

        return sessionId.trim();
    }

    private String normalizeUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return defaultUserId;
        }

        return userId.trim();
    }
}
