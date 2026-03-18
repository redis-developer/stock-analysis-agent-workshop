package com.redis.stockanalysisagent.agent.orchestration;

import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

public record TokenUsageSummary(
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens
) {

    public static TokenUsageSummary from(ChatResponse response) {
        if (response == null) {
            return null;
        }

        TokenUsageSummary metadataUsage = from(response.getMetadata());
        if (metadataUsage != null) {
            return metadataUsage;
        }

        if (response.getResult() == null || response.getResult().getMetadata() == null) {
            return null;
        }

        return from(response.getResult().getMetadata());
    }

    public static TokenUsageSummary from(ChatResponseMetadata metadata) {
        if (metadata == null) {
            return null;
        }

        return from(metadata.getUsage());
    }

    public static TokenUsageSummary from(Usage usage) {
        if (usage == null) {
            return null;
        }

        Integer promptTokens = normalize(usage.getPromptTokens());
        Integer completionTokens = normalize(usage.getCompletionTokens());
        Integer totalTokens = normalize(usage.getTotalTokens());
        if (promptTokens != null || completionTokens != null || totalTokens != null) {
            return new TokenUsageSummary(promptTokens, completionTokens, totalTokens);
        }

        return from(usage.getNativeUsage());
    }

    public static TokenUsageSummary from(ChatGenerationMetadata metadata) {
        if (metadata == null) {
            return null;
        }

        TokenUsageSummary usageValue = from((Object) metadata.getOrDefault("usage", null));
        if (usageValue != null) {
            return usageValue;
        }

        Integer promptTokens = parseInteger(
                firstNonNull(
                        metadata.getOrDefault("promptTokens", null),
                        metadata.getOrDefault("prompt_tokens", null),
                        metadata.getOrDefault("inputTokens", null),
                        metadata.getOrDefault("input_tokens", null)
                )
        );
        Integer completionTokens = parseInteger(
                firstNonNull(
                        metadata.getOrDefault("completionTokens", null),
                        metadata.getOrDefault("completion_tokens", null),
                        metadata.getOrDefault("outputTokens", null),
                        metadata.getOrDefault("output_tokens", null)
                )
        );
        Integer totalTokens = parseInteger(
                firstNonNull(
                        metadata.getOrDefault("totalTokens", null),
                        metadata.getOrDefault("total_tokens", null)
                )
        );

        if (promptTokens == null && completionTokens == null && totalTokens == null) {
            return null;
        }

        return new TokenUsageSummary(promptTokens, completionTokens, totalTokens);
    }

    public static TokenUsageSummary from(Object usageValue) {
        if (usageValue == null) {
            return null;
        }

        if (usageValue instanceof TokenUsageSummary summary) {
            return summary;
        }

        if (usageValue instanceof Usage usage) {
            return from(usage);
        }

        if (usageValue instanceof Map<?, ?> map) {
            Integer promptTokens = parseInteger(firstNonNull(
                    map.get("promptTokens"),
                    map.get("prompt_tokens"),
                    map.get("inputTokens"),
                    map.get("input_tokens")
            ));
            Integer completionTokens = parseInteger(firstNonNull(
                    map.get("completionTokens"),
                    map.get("completion_tokens"),
                    map.get("outputTokens"),
                    map.get("output_tokens")
            ));
            Integer totalTokens = parseInteger(firstNonNull(
                    map.get("totalTokens"),
                    map.get("total_tokens")
            ));

            if (promptTokens == null && completionTokens == null && totalTokens == null) {
                return null;
            }

            return new TokenUsageSummary(promptTokens, completionTokens, totalTokens);
        }

        return fromAccessorMethods(usageValue);
    }

    private static TokenUsageSummary fromAccessorMethods(Object usageValue) {
        Integer promptTokens = parseInteger(readAccessor(usageValue, "getPromptTokens", "promptTokens"));
        Integer completionTokens = parseInteger(readAccessor(usageValue, "getCompletionTokens", "completionTokens"));
        Integer totalTokens = parseInteger(readAccessor(usageValue, "getTotalTokens", "totalTokens"));

        if (promptTokens == null && completionTokens == null && totalTokens == null) {
            return null;
        }

        return new TokenUsageSummary(promptTokens, completionTokens, totalTokens);
    }

    private static Object readAccessor(Object target, String... methodNames) {
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                if (method.getParameterCount() == 0) {
                    return method.invoke(target);
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        return null;
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

    public boolean hasValues() {
        return promptTokens != null || completionTokens != null || totalTokens != null;
    }

    private static Integer normalize(Integer value) {
        return value == null || value < 0 ? null : value;
    }

    private static Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return normalize(number.intValue());
        }

        if (value instanceof String stringValue) {
            try {
                return normalize(Integer.parseInt(stringValue.trim()));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }

    private static Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
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
