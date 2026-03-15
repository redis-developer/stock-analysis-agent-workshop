package com.redis.stockanalysisagent.agent.coordinatoragent;

import com.redis.stockanalysisagent.agent.AgentType;
import com.redis.stockanalysisagent.api.AnalysisRequest;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CoordinatorAgentTest {

    @Test
    void usesRoutingAgentOutputForSimplePriceRequests() {
        CoordinatorAgent coordinatorAgent = new CoordinatorAgent(new StubCoordinatorRoutingAgent(
                RoutingDecision.of(
                        java.util.List.of(AgentType.MARKET_DATA),
                        false,
                        "A direct market quote is enough."
                )
        ));

        ExecutionPlan plan = coordinatorAgent.createPlan(new AnalysisRequest("MSFT", "What is the stock price right now?"));

        assertThat(plan.requiresSynthesis()).isFalse();
        assertThat(plan.selectedAgents()).containsExactly(AgentType.MARKET_DATA);
        assertThat(plan.routingReasoning()).isEqualTo("A direct market quote is enough.");
    }

    @Test
    void appendsSynthesisWhenRoutingDecisionRequiresIt() {
        CoordinatorAgent coordinatorAgent = new CoordinatorAgent(new StubCoordinatorRoutingAgent(
                RoutingDecision.of(
                        java.util.List.of(AgentType.MARKET_DATA, AgentType.FUNDAMENTALS, AgentType.NEWS),
                        true,
                        "The question asks for a combined assessment."
                )
        ));

        ExecutionPlan plan = coordinatorAgent.createPlan(new AnalysisRequest(
                "TSLA",
                "Is Tesla overvalued based on fundamentals and current news?"
        ));

        assertThat(plan.requiresSynthesis()).isTrue();
        assertThat(plan.selectedAgents()).containsExactly(
                AgentType.MARKET_DATA,
                AgentType.FUNDAMENTALS,
                AgentType.NEWS,
                AgentType.SYNTHESIS
        );
    }

    @Test
    void removesDuplicateAndSyntheticAgentsFromRoutingOutputBeforeNormalizingPlan() {
        CoordinatorAgent coordinatorAgent = new CoordinatorAgent(new StubCoordinatorRoutingAgent(
                RoutingDecision.of(
                        java.util.List.of(AgentType.MARKET_DATA, AgentType.SYNTHESIS, AgentType.MARKET_DATA),
                        true,
                        "The model accidentally included SYNTHESIS in the specialized list."
                )
        ));

        ExecutionPlan plan = coordinatorAgent.createPlan(new AnalysisRequest("AAPL", "Give me a market update."));

        assertThat(plan.selectedAgents()).containsExactly(
                AgentType.MARKET_DATA,
                AgentType.SYNTHESIS
        );
    }

    @Test
    void carriesConversationIdWhenCoordinatorNeedsMoreInput() {
        CoordinatorAgent coordinatorAgent = new CoordinatorAgent(new StubCoordinatorRoutingAgent(
                RoutingDecision.needsMoreInput("Which ticker should I look up?")
        ));

        RoutingDecision decision = coordinatorAgent.execute("What's the current price?", "conversation-123");

        assertThat(decision.getFinishReason()).isEqualTo(RoutingDecision.FinishReason.NEEDS_MORE_INPUT);
        assertThat(decision.getConversationId()).isEqualTo("conversation-123");
        assertThat(decision.getNextPrompt()).isEqualTo("Which ticker should I look up?");
    }

    @Test
    void convertsCompletedCoordinatorDecisionIntoNormalizedAnalysisRequest() {
        CoordinatorAgent coordinatorAgent = new CoordinatorAgent(new StubCoordinatorRoutingAgent(
                RoutingDecision.completed(
                        "aapl",
                        "What is the current price?",
                        java.util.List.of(AgentType.MARKET_DATA),
                        false,
                        "A direct market quote is enough."
                )
        ));

        AnalysisRequest request = coordinatorAgent.toAnalysisRequest(RoutingDecision.completed(
                "aapl",
                "What is the current price?",
                java.util.List.of(AgentType.MARKET_DATA),
                false,
                "A direct market quote is enough."
        ));

        assertThat(request.ticker()).isEqualTo("AAPL");
        assertThat(request.question()).isEqualTo("What is the current price?");
    }

    private static final class StubCoordinatorRoutingAgent extends CoordinatorRoutingAgent {

        private final RoutingDecision routingDecision;

        private StubCoordinatorRoutingAgent(RoutingDecision routingDecision) {
            super(Optional.empty());
            this.routingDecision = routingDecision;
        }

        @Override
        public RoutingDecision route(AnalysisRequest request) {
            return routingDecision;
        }

        @Override
        public RoutingDecision route(String userMessage, String conversationId) {
            return routingDecision;
        }
    }
}
