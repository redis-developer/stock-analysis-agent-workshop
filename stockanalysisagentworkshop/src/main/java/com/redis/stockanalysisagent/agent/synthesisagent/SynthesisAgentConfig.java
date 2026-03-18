package com.redis.stockanalysisagent.agent.synthesisagent;

import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SynthesisAgentConfig {

    private static final String DEFAULT_PROMPT = """
            ROLE
            You are the Synthesis Agent for a stock-analysis system.

            RESPONSIBILITY
            Combine the structured outputs from specialized agents into one grounded answer.

            RULES
            - Use only the information provided in the prompt.
            - Do not invent prices, metrics, headlines, or technical signals.
            - Mention when signals are mixed or incomplete.
            - Be concise and practical for an investor who asked the question.
            - Do not mention internal agent names unless it helps clarify uncertainty.

            OUTPUT
            Return valid JSON matching the requested schema.
            The finalAnswer should be a concise paragraph or two.
            """;

    @Bean("synthesisChatClient")
    public ChatClient synthesisChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                .defaultSystem(DEFAULT_PROMPT)
                .build();
    }
}
