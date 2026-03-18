package com.redis.stockanalysisagent.agent.synthesisagent;

import com.redis.stockanalysisagent.agent.fundamentalsagent.FundamentalsSnapshot;
import com.redis.stockanalysisagent.agent.marketdataagent.MarketSnapshot;
import com.redis.stockanalysisagent.agent.newsagent.NewsSnapshot;
import com.redis.stockanalysisagent.agent.orchestration.AnalysisRequest;
import com.redis.stockanalysisagent.agent.orchestration.TokenUsageSummary;
import com.redis.stockanalysisagent.agent.technicalanalysisagent.TechnicalAnalysisSnapshot;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class SynthesisAgent {

    private final ChatClient synthesisChatClient;

    public SynthesisAgent(@Qualifier("synthesisChatClient") ChatClient synthesisChatClient) {
        this.synthesisChatClient = synthesisChatClient;
    }

    public SynthesisResult synthesize(
            AnalysisRequest request,
            MarketSnapshot marketSnapshot,
            FundamentalsSnapshot fundamentalsSnapshot,
            NewsSnapshot newsSnapshot,
            TechnicalAnalysisSnapshot technicalAnalysisSnapshot
    ) {
        ResponseEntity<ChatResponse, SynthesisResponse> response = synthesisChatClient
                .prompt()
                .user(buildPrompt(request, marketSnapshot, fundamentalsSnapshot, newsSnapshot, technicalAnalysisSnapshot))
                .call()
                .responseEntity(SynthesisResponse.class);

        return new SynthesisResult(
                response.entity().finalAnswer().trim(),
                TokenUsageSummary.from(response.response())
        );
    }

    private String buildPrompt(
            AnalysisRequest request,
            MarketSnapshot marketSnapshot,
            FundamentalsSnapshot fundamentalsSnapshot,
            NewsSnapshot newsSnapshot,
            TechnicalAnalysisSnapshot technicalAnalysisSnapshot
    ) {
        return """
                QUESTION
                %s

                REQUESTED_TICKER
                %s

                MARKET_DATA
                %s

                FUNDAMENTALS
                %s

                NEWS
                %s

                TECHNICAL_ANALYSIS
                %s

                INSTRUCTIONS
                - Answer the user directly.
                - Weigh the available signals and call out conflicts or uncertainty.
                - Keep the response grounded in these inputs only.
                """.formatted(
                request.question(),
                request.ticker(),
                section(marketSnapshot, "No market data available."),
                section(fundamentalsSnapshot, "No fundamentals data available."),
                section(newsSnapshot, "No news data available."),
                section(technicalAnalysisSnapshot, "No technical-analysis data available.")
        );
    }

    private String section(Object value, String emptyValue) {
        return value == null ? emptyValue : value.toString();
    }
}
