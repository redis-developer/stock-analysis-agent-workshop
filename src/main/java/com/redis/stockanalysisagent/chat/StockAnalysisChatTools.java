package com.redis.stockanalysisagent.chat;

import com.redis.stockanalysisagent.agent.AgentOrchestrationService;
import com.redis.stockanalysisagent.agent.coordinatoragent.CoordinatorAgent;
import com.redis.stockanalysisagent.agent.coordinatoragent.RoutingDecision;
import com.redis.stockanalysisagent.api.AnalysisRequest;
import com.redis.stockanalysisagent.api.AnalysisResponse;
import com.redis.stockanalysisagent.semanticcache.SemanticAnalysisCache;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class StockAnalysisChatTools {

    private static final ToolResultMetadata NOT_FROM_CACHE = new ToolResultMetadata(false);

    private final CoordinatorAgent coordinatorAgent;
    private final AgentOrchestrationService agentOrchestrationService;
    private final SemanticAnalysisCache semanticAnalysisCache;
    private final ThreadLocal<ToolResultMetadata> invocationMetadata = ThreadLocal.withInitial(() -> NOT_FROM_CACHE);

    public StockAnalysisChatTools(
            CoordinatorAgent coordinatorAgent,
            AgentOrchestrationService agentOrchestrationService,
            SemanticAnalysisCache semanticAnalysisCache
    ) {
        this.coordinatorAgent = coordinatorAgent;
        this.agentOrchestrationService = agentOrchestrationService;
        this.semanticAnalysisCache = semanticAnalysisCache;
    }

    @Tool(description = "Run the stock-analysis orchestration for a user's question. Use this for market, fundamentals, news, technical, or combined stock-analysis requests. The tool may return a clarification question if the request is incomplete.")
    public String analyzeStockRequest(
            @ToolParam(description = "The user's stock-analysis request in plain English, including any ticker or company reference resolved from conversation context.")
            String request
    ) {
        invocationMetadata.set(NOT_FROM_CACHE);

        java.util.Optional<String> cachedResponse = semanticAnalysisCache.findAnswer(request);
        if (cachedResponse.isPresent()) {
            invocationMetadata.set(new ToolResultMetadata(true));
            return cachedResponse.get();
        }

        RoutingDecision routingDecision = coordinatorAgent.execute(request);

        if (routingDecision.getFinishReason() == RoutingDecision.FinishReason.NEEDS_MORE_INPUT) {
            return routingDecision.getNextPrompt();
        }

        if (routingDecision.getFinishReason() != RoutingDecision.FinishReason.COMPLETED) {
            return resolveCoordinatorMessage(routingDecision);
        }

        AnalysisRequest analysisRequest = coordinatorAgent.toAnalysisRequest(routingDecision);
        AnalysisResponse response = agentOrchestrationService.processRequest(analysisRequest, routingDecision);
        String renderedResponse = renderAnalysis(response);
        if (response.limitations().isEmpty()) {
            semanticAnalysisCache.store(request, renderedResponse);
        }
        return renderedResponse;
    }

    public void resetInvocationMetadata() {
        invocationMetadata.remove();
    }

    public ToolResultMetadata consumeInvocationMetadata() {
        ToolResultMetadata metadata = invocationMetadata.get();
        invocationMetadata.remove();
        return metadata == null ? NOT_FROM_CACHE : metadata;
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

    public record ToolResultMetadata(
            boolean fromSemanticCache
    ) {
    }
}
