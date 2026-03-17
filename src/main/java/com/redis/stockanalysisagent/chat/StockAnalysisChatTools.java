package com.redis.stockanalysisagent.chat;

import com.redis.stockanalysisagent.agent.AgentOrchestrationService;
import com.redis.stockanalysisagent.agent.coordinatoragent.CoordinatorAgent;
import com.redis.stockanalysisagent.agent.coordinatoragent.RoutingDecision;
import com.redis.stockanalysisagent.api.AnalysisRequest;
import com.redis.stockanalysisagent.api.AnalysisResponse;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class StockAnalysisChatTools {

    private final CoordinatorAgent coordinatorAgent;
    private final AgentOrchestrationService agentOrchestrationService;

    public StockAnalysisChatTools(
            CoordinatorAgent coordinatorAgent,
            AgentOrchestrationService agentOrchestrationService
    ) {
        this.coordinatorAgent = coordinatorAgent;
        this.agentOrchestrationService = agentOrchestrationService;
    }

    @Tool(description = "Run the stock-analysis orchestration for a user's question. Use this for market, fundamentals, news, technical, or combined stock-analysis requests. The tool may return a clarification question if the request is incomplete.")
    public String analyzeStockRequest(
            @ToolParam(description = "The user's stock-analysis request in plain English, including any ticker or company reference resolved from conversation context.")
            String request
    ) {
        RoutingDecision routingDecision = coordinatorAgent.execute(request);

        if (routingDecision.getFinishReason() == RoutingDecision.FinishReason.NEEDS_MORE_INPUT) {
            return routingDecision.getNextPrompt();
        }

        if (routingDecision.getFinishReason() != RoutingDecision.FinishReason.COMPLETED) {
            return resolveCoordinatorMessage(routingDecision);
        }

        AnalysisRequest analysisRequest = coordinatorAgent.toAnalysisRequest(routingDecision);
        AnalysisResponse response = agentOrchestrationService.processRequest(analysisRequest, routingDecision);
        return renderAnalysis(response);
    }

    private String resolveCoordinatorMessage(RoutingDecision routingDecision) {
        if (routingDecision.getFinalResponse() != null && !routingDecision.getFinalResponse().isBlank()) {
            return routingDecision.getFinalResponse();
        }

        if (routingDecision.getNextPrompt() != null && !routingDecision.getNextPrompt().isBlank()) {
            return routingDecision.getNextPrompt();
        }

        return "I could not complete the stock-analysis request.";
    }

    private String renderAnalysis(AnalysisResponse response) {
        if (response.limitations().isEmpty()) {
            return response.answer();
        }

        return "%s\n\nLimitations: %s"
                .formatted(response.answer(), String.join(" ", response.limitations()));
    }
}
