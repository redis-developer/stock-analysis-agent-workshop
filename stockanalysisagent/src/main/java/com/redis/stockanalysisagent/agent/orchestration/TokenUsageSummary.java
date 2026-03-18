package com.redis.stockanalysisagent.agent.orchestration;

import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.Collection;

public record TokenUsageSummary(
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens
) {

    public static TokenUsageSummary from(ChatResponse response) {
        return response == null ? null : from(response.getMetadata());
    }

    public static TokenUsageSummary from(ChatResponseMetadata metadata) {
        return metadata == null ? null : from(metadata.getUsage());
    }

    public static TokenUsageSummary from(Usage usage) {
        if (usage == null) {
            return null;
        }

        return new TokenUsageSummary(
                normalize(usage.getPromptTokens()),
                normalize(usage.getCompletionTokens()),
                normalize(usage.getTotalTokens())
        );
    }

    public static TokenUsageSummary sum(Collection<TokenUsageSummary> usages) {
        TokenUsageSummary total = null;
        for (TokenUsageSummary usage : usages) {
            if (usage == null) {
                continue;
            }
            total = total == null ? usage : total.plus(usage);
        }
        return total;
    }

    public TokenUsageSummary plus(TokenUsageSummary other) {
        if (other == null) {
            return this;
        }

        return new TokenUsageSummary(
                sum(promptTokens, other.promptTokens),
                sum(completionTokens, other.completionTokens),
                sum(totalTokens, other.totalTokens)
        );
    }

    private static Integer normalize(Integer value) {
        return value == null || value < 0 ? null : value;
    }

    private static Integer sum(Integer left, Integer right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left + right;
    }
}
