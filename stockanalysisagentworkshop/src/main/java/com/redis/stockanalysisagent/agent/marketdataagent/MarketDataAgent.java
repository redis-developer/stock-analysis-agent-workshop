package com.redis.stockanalysisagent.agent.marketdataagent;

import com.redis.stockanalysisagent.agent.orchestration.TokenUsageSummary;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class MarketDataAgent {

    private final ChatClient marketDataChatClient;

    public MarketDataAgent(@Qualifier("marketDataChatClient") ChatClient marketDataChatClient) {
        this.marketDataChatClient = marketDataChatClient;
    }

    public MarketDataResult execute(String ticker, String question) {
        // PART 2 STEP 4:
        // Replace this method body with the snippet from the Part 2 guide.
        throw new UnsupportedOperationException("Part 2: implement execute(...)");
    }

    private String buildPrompt(String ticker, String question) {
        // PART 2 STEP 3:
        // Replace this method body with the snippet from the Part 2 guide.
        throw new UnsupportedOperationException("Part 2: implement buildPrompt(...)");
    }
}
