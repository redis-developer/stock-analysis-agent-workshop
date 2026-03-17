package com.redis.stockanalysisagent.chat;

import com.redis.stockanalysisagent.agent.AgentExecution;
import com.redis.stockanalysisagent.agent.AgentExecutionStatus;
import com.redis.stockanalysisagent.agent.AgentOrchestrationService;
import com.redis.stockanalysisagent.agent.AgentType;
import com.redis.stockanalysisagent.agent.coordinatoragent.CoordinatorAgent;
import com.redis.stockanalysisagent.agent.coordinatoragent.ExecutionPlan;
import com.redis.stockanalysisagent.agent.coordinatoragent.RoutingDecision;
import com.redis.stockanalysisagent.api.AnalysisRequest;
import com.redis.stockanalysisagent.api.AnalysisResponse;
import com.redis.stockanalysisagent.semanticcache.SemanticAnalysisCache;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockAnalysisChatToolsTest {

    @Test
    void returnsSemanticCacheHitBeforeCoordinator() {
        CoordinatorAgent coordinatorAgent = mock(CoordinatorAgent.class);
        AgentOrchestrationService orchestrationService = mock(AgentOrchestrationService.class);
        SemanticAnalysisCache semanticAnalysisCache = mock(SemanticAnalysisCache.class);

        when(semanticAnalysisCache.findAnswer("What is Apple's current price?"))
                .thenReturn(Optional.of("Apple is trading at $200.00."));

        StockAnalysisChatTools chatTools = new StockAnalysisChatTools(
                coordinatorAgent,
                orchestrationService,
                semanticAnalysisCache
        );

        String response = chatTools.analyzeStockRequest("What is Apple's current price?");
        StockAnalysisChatTools.ToolResultMetadata metadata = chatTools.consumeInvocationMetadata();

        assertThat(response).isEqualTo("Apple is trading at $200.00.");
        assertThat(metadata.fromSemanticCache()).isTrue();
        assertThat(metadata.triggeredAgents()).isEmpty();
        verify(coordinatorAgent, never()).execute(any(String.class));
        verify(orchestrationService, never()).processRequest(any(), any());
    }

    @Test
    void storesSuccessfulCompletedAnalysisResponses() {
        CoordinatorAgent coordinatorAgent = mock(CoordinatorAgent.class);
        AgentOrchestrationService orchestrationService = mock(AgentOrchestrationService.class);
        SemanticAnalysisCache semanticAnalysisCache = mock(SemanticAnalysisCache.class);

        RoutingDecision routingDecision = RoutingDecision.completed(
                "AAPL",
                "What is Apple's current price?",
                List.of(AgentType.MARKET_DATA),
                false,
                "Simple price lookup."
        );
        AnalysisRequest analysisRequest = new AnalysisRequest("AAPL", "What is Apple's current price?");
        AnalysisResponse analysisResponse = new AnalysisResponse(
                "AAPL",
                "What is Apple's current price?",
                OffsetDateTime.parse("2026-03-17T00:00:00Z"),
                new ExecutionPlan(List.of(AgentType.MARKET_DATA), false, "Simple price lookup."),
                List.of(new AgentExecution(AgentType.MARKET_DATA, AgentExecutionStatus.COMPLETED, "Market data completed.")),
                null,
                null,
                null,
                null,
                "Apple is trading at $200.00.",
                List.of()
        );

        when(semanticAnalysisCache.findAnswer("What is Apple's current price?"))
                .thenReturn(Optional.empty());
        when(coordinatorAgent.execute("What is Apple's current price?"))
                .thenReturn(routingDecision);
        when(coordinatorAgent.toAnalysisRequest(routingDecision))
                .thenReturn(analysisRequest);
        when(orchestrationService.processRequest(analysisRequest, routingDecision))
                .thenReturn(analysisResponse);

        StockAnalysisChatTools chatTools = new StockAnalysisChatTools(
                coordinatorAgent,
                orchestrationService,
                semanticAnalysisCache
        );

        String response = chatTools.analyzeStockRequest("What is Apple's current price?");
        StockAnalysisChatTools.ToolResultMetadata metadata = chatTools.consumeInvocationMetadata();

        assertThat(response).isEqualTo("Apple is trading at $200.00.");
        assertThat(metadata.fromSemanticCache()).isFalse();
        assertThat(metadata.triggeredAgents()).containsExactly("MARKET_DATA");
        verify(semanticAnalysisCache).store("What is Apple's current price?", "Apple is trading at $200.00.");
    }
}
