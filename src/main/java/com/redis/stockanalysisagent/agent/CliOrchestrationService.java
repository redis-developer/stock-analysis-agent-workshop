package com.redis.stockanalysisagent.agent;

import com.redis.stockanalysisagent.chat.StockAnalysisChatService;
import org.springframework.stereotype.Service;

import java.util.Scanner;
import java.util.UUID;

@Service
public class CliOrchestrationService {

    private final StockAnalysisChatService stockAnalysisChatService;
    private final String defaultUserId;
    private final Scanner scanner;

    public CliOrchestrationService(
            StockAnalysisChatService stockAnalysisChatService,
            @org.springframework.beans.factory.annotation.Value("${app.chat.user-id:workshop-user}") String defaultUserId
    ) {
        this.stockAnalysisChatService = stockAnalysisChatService;
        this.defaultUserId = defaultUserId;
        this.scanner = new Scanner(System.in);
    }

    public void processRequest() {
        String sessionId = UUID.randomUUID().toString();
        String conversationId = defaultUserId + ":" + sessionId;

        System.out.println();
        System.out.println("═".repeat(80));
        System.out.println("Stock Analysis Chat");
        System.out.println("═".repeat(80));
        System.out.println();
        System.out.println("User: " + defaultUserId);
        System.out.println("Conversation: " + conversationId);
        System.out.println("Commands: /exit to quit, /clear to reset this chat session");
        System.out.println();

        while (true) {
            String message = prompt("You");
            if (message == null || message.isBlank()) {
                continue;
            }
            if ("/exit".equalsIgnoreCase(message.trim())) {
                System.out.println();
                System.out.println("Ending chat session.");
                System.out.println("═".repeat(80));
                return;
            }
            if ("/clear".equalsIgnoreCase(message.trim())) {
                stockAnalysisChatService.clearSession(defaultUserId, sessionId);
                System.out.println();
                System.out.println("Assistant");
                System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                System.out.println("Chat memory for this session was cleared.");
                System.out.println();
                continue;
            }

            StockAnalysisChatService.ChatTurn turn = stockAnalysisChatService.chat(defaultUserId, sessionId, message);
            System.out.println();
            System.out.println("Assistant");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println(turn.response());
            if (turn.contextPercentage() != null) {
                System.out.println();
                System.out.println("Memory context used: " + Math.round(turn.contextPercentage() * 100) + "%");
            }
            if (turn.retrievedMemories() != null && !turn.retrievedMemories().isEmpty()) {
                System.out.println("Retrieved memories:");
                turn.retrievedMemories().stream()
                        .limit(3)
                        .forEach(memory -> System.out.println("- " + memory));
            }
            System.out.println();
        }
    }

    private String prompt(String label) {
        System.out.print(label + ": ");
        return scanner.nextLine();
    }
}
