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
                List.of(new AgentExecution(AgentType.MARKET_DATA, AgentExecutionStatus.COMPLETED, "Market data completed.", 125)),
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
        assertThat(metadata.triggeredAgents())
                .extracting(ChatExecutionStep::agentType)
                .containsExactly("COORDINATOR", "MARKET_DATA");
        assertThat(metadata.triggeredAgents())
                .filteredOn(step -> "COORDINATOR".equals(step.agentType()))
                .singleElement()
                .satisfies(step -> assertThat(step.summary()).contains("Resolved AAPL"));
        assertThat(metadata.triggeredAgents())
                .filteredOn(step -> "MARKET_DATA".equals(step.agentType()))
                .singleElement()
                .satisfies(step -> {
                    assertThat(step.durationMs()).isEqualTo(125);
                    assertThat(step.summary()).isEqualTo("Market data completed.");
                });
        verify(semanticAnalysisCache).store("What is Apple's current price?", "Apple is trading at $200.00.");
    }

    @Test
    void accumulatesTriggeredAgentsAcrossMultipleToolCallsInTheSameTurn() {
        CoordinatorAgent coordinatorAgent = mock(CoordinatorAgent.class);
        AgentOrchestrationService orchestrationService = mock(AgentOrchestrationService.class);
        SemanticAnalysisCache semanticAnalysisCache = mock(SemanticAnalysisCache.class);

        RoutingDecision fundamentalsDecision = RoutingDecision.completed(
                "TSLA",
                "How do Tesla's fundamentals look?",
                List.of(AgentType.FUNDAMENTALS),
                false,
                "Fundamentals request."
        );
        RoutingDecision technicalDecision = RoutingDecision.completed(
                "TSLA",
                "What do Tesla's technicals look like?",
                List.of(AgentType.TECHNICAL_ANALYSIS),
                false,
                "Technical request."
        );

        AnalysisRequest fundamentalsRequest = new AnalysisRequest("TSLA", "How do Tesla's fundamentals look?");
        AnalysisRequest technicalRequest = new AnalysisRequest("TSLA", "What do Tesla's technicals look like?");

        AnalysisResponse fundamentalsResponse = new AnalysisResponse(
                "TSLA",
                "How do Tesla's fundamentals look?",
                OffsetDateTime.parse("2026-03-17T00:00:00Z"),
                new ExecutionPlan(List.of(AgentType.FUNDAMENTALS), false, "Fundamentals request."),
                List.of(new AgentExecution(AgentType.FUNDAMENTALS, AgentExecutionStatus.COMPLETED, "Fundamentals completed.", 310)),
                null,
                null,
                null,
                null,
                "Tesla fundamentals summary.",
                List.of()
        );
        AnalysisResponse technicalResponse = new AnalysisResponse(
                "TSLA",
                "What do Tesla's technicals look like?",
                OffsetDateTime.parse("2026-03-17T00:00:01Z"),
                new ExecutionPlan(List.of(AgentType.TECHNICAL_ANALYSIS), false, "Technical request."),
                List.of(new AgentExecution(AgentType.TECHNICAL_ANALYSIS, AgentExecutionStatus.COMPLETED, "Technical completed.", 470)),
                null,
                null,
                null,
                null,
                "Tesla technical summary.",
                List.of()
        );

        when(semanticAnalysisCache.findAnswer(any(String.class))).thenReturn(Optional.empty());
        when(coordinatorAgent.execute("How do Tesla's fundamentals look?")).thenReturn(fundamentalsDecision);
        when(coordinatorAgent.execute("What do Tesla's technicals look like?")).thenReturn(technicalDecision);
        when(coordinatorAgent.toAnalysisRequest(fundamentalsDecision)).thenReturn(fundamentalsRequest);
        when(coordinatorAgent.toAnalysisRequest(technicalDecision)).thenReturn(technicalRequest);
        when(orchestrationService.processRequest(fundamentalsRequest, fundamentalsDecision)).thenReturn(fundamentalsResponse);
        when(orchestrationService.processRequest(technicalRequest, technicalDecision)).thenReturn(technicalResponse);

        StockAnalysisChatTools chatTools = new StockAnalysisChatTools(
                coordinatorAgent,
                orchestrationService,
                semanticAnalysisCache
        );

        chatTools.analyzeStockRequest("How do Tesla's fundamentals look?");
        chatTools.analyzeStockRequest("What do Tesla's technicals look like?");
        StockAnalysisChatTools.ToolResultMetadata metadata = chatTools.consumeInvocationMetadata();

        assertThat(metadata.fromSemanticCache()).isFalse();
        assertThat(metadata.triggeredAgents())
                .extracting(ChatExecutionStep::agentType)
                .containsExactly("COORDINATOR", "FUNDAMENTALS", "TECHNICAL_ANALYSIS");
        assertThat(metadata.triggeredAgents())
                .filteredOn(step -> "COORDINATOR".equals(step.agentType()))
                .singleElement()
                .satisfies(step -> assertThat(step.summary()).contains("Resolved TSLA"));
        assertThat(metadata.triggeredAgents())
                .filteredOn(step -> !"COORDINATOR".equals(step.agentType()))
                .extracting(ChatExecutionStep::durationMs)
                .containsExactly(310L, 470L);
        assertThat(metadata.triggeredAgents())
                .filteredOn(step -> "FUNDAMENTALS".equals(step.agentType()))
                .singleElement()
                .satisfies(step -> assertThat(step.summary()).isEqualTo("Fundamentals completed."));
    }
}
