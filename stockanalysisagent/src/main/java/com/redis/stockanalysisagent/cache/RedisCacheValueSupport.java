package com.redis.stockanalysisagent.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class RedisCacheValueSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private RedisCacheValueSupport() {
    }

    public static <T> T normalize(Object payload, Class<T> targetType, String description) {
        if (payload == null) {
            throw new IllegalStateException(description + " returned an empty response.");
        }

        if (targetType.isInstance(payload)) {
            return targetType.cast(payload);
        }

        return OBJECT_MAPPER.convertValue(payload, targetType);
    }
}
