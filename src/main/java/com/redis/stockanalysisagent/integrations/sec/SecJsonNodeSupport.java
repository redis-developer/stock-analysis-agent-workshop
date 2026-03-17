package com.redis.stockanalysisagent.integrations.sec;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class SecJsonNodeSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SecJsonNodeSupport() {
    }

    public static JsonNode normalize(Object payload, String description) {
        if (payload == null) {
            throw new IllegalStateException(description + " returned an empty response.");
        }

        if (payload instanceof JsonNode jsonNode) {
            if (jsonNode.size() == 0) {
                throw new IllegalStateException(description + " returned an empty response.");
            }
            return jsonNode;
        }

        JsonNode normalized = OBJECT_MAPPER.valueToTree(payload);
        if (normalized == null || normalized.size() == 0) {
            throw new IllegalStateException(description + " returned an empty response.");
        }
        return normalized;
    }
}
