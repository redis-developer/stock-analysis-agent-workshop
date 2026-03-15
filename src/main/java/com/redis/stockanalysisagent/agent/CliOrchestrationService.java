package com.redis.stockanalysisagent.agent;

import com.redis.stockanalysisagent.agent.coordinatoragent.CoordinatorAgent;
import com.redis.stockanalysisagent.agent.coordinatoragent.RoutingDecision;
import com.redis.stockanalysisagent.api.AnalysisRequest;
import com.redis.stockanalysisagent.api.AnalysisResponse;
import org.springframework.stereotype.Service;

import java.util.Scanner;

@Service
public class CliOrchestrationService {

    private final AgentOrchestrationService agentOrchestrationService;
    private final CoordinatorAgent coordinatorAgent;
    private final Scanner scanner;

    public CliOrchestrationService(
            AgentOrchestrationService agentOrchestrationService,
            CoordinatorAgent coordinatorAgent
    ) {
        this.agentOrchestrationService = agentOrchestrationService;
        this.coordinatorAgent = coordinatorAgent;
        this.scanner = new Scanner(System.in);
    }

    public void processRequest() {
        String initialRequest = prompt("Request");

        System.out.println();
        System.out.println("═".repeat(80));
        System.out.println("Stock Analysis CLI");
        System.out.println("═".repeat(80));
        System.out.println();

        System.out.println("Request");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("Message: " + initialRequest);
        System.out.println();

        System.out.println("Stage 1/3: Coordinator Agent");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        RoutingDecision routingDecision = coordinatorAgent.execute(initialRequest);
        while (routingDecision.getFinishReason() == RoutingDecision.FinishReason.NEEDS_MORE_INPUT) {
            System.out.println("Needs more input: " + routingDecision.getNextPrompt());
            String userResponse = prompt("Your answer");
            routingDecision = coordinatorAgent.execute(userResponse, routingDecision.getConversationId());
        }

        if (routingDecision.getFinishReason() != RoutingDecision.FinishReason.COMPLETED) {
            printCoordinatorSummary(routingDecision, null);
            System.out.println();
            System.out.println("Stage 2/2: Final Answer");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println(resolveCoordinatorMessage(routingDecision));
            System.out.println();
            System.out.println("═".repeat(80));
            return;
        }

        AnalysisRequest resolvedRequest = coordinatorAgent.toAnalysisRequest(routingDecision);
        AnalysisResponse response = agentOrchestrationService.processRequest(resolvedRequest, routingDecision);

        printCoordinatorSummary(routingDecision, response);

        System.out.println("Stage 2/3: Agent Execution");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        response.agentExecutions().forEach(execution ->
                System.out.println(execution.agentType() + " -> " + execution.status() + " | " + execution.summary())
        );
        if (response.marketSnapshot() != null) {
            System.out.println();
            System.out.println("Market snapshot");
            System.out.println("Symbol: " + response.marketSnapshot().symbol());
            System.out.println("Current price: $" + response.marketSnapshot().currentPrice());
            System.out.println("Previous close: $" + response.marketSnapshot().previousClose());
            System.out.println("Change: $" + response.marketSnapshot().absoluteChange()
                    + " (" + response.marketSnapshot().percentChange() + "%)");
            System.out.println("Source: " + response.marketSnapshot().source());
        }
        System.out.println();

        System.out.println("Stage 3/3: Final Answer");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println(response.answer());
        if (!response.limitations().isEmpty()) {
            System.out.println();
            System.out.println("Limitations");
            response.limitations().forEach(limit -> System.out.println("- " + limit));
        }
        System.out.println();
        System.out.println("═".repeat(80));
    }

    private String prompt(String label) {
        System.out.print(label + ": ");
        return scanner.nextLine();
    }

    private void printCoordinatorSummary(RoutingDecision routingDecision, AnalysisResponse response) {
        if (response != null) {
            System.out.println("Resolved ticker: " + response.ticker());
            System.out.println("Resolved question: " + response.question());
            System.out.println("Selected agents: " + response.executionPlan().selectedAgents());
            if (response.executionPlan().routingReasoning() != null
                    && !response.executionPlan().routingReasoning().isBlank()) {
                System.out.println("Routing reasoning: " + response.executionPlan().routingReasoning());
            }
        } else {
            System.out.println("Finish reason: " + routingDecision.getFinishReason());
            if (routingDecision.getReasoning() != null && !routingDecision.getReasoning().isBlank()) {
                System.out.println("Routing reasoning: " + routingDecision.getReasoning());
            }
        }
        System.out.println();
    }

    private String resolveCoordinatorMessage(RoutingDecision routingDecision) {
        if (routingDecision.getFinalResponse() != null && !routingDecision.getFinalResponse().isBlank()) {
            return routingDecision.getFinalResponse();
        }

        if (routingDecision.getNextPrompt() != null && !routingDecision.getNextPrompt().isBlank()) {
            return routingDecision.getNextPrompt();
        }

        return "The coordinator could not complete the request.";
    }
}
