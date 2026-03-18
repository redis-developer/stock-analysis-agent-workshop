package com.redis.stockanalysisagent.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

@Component
public class ExternalDataCache {

    private final CacheManager cacheManager;
    private final ConcurrentMap<String, Object> locks = new ConcurrentHashMap<>();

    public ExternalDataCache(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @SuppressWarnings("unchecked")
    public <T> T getOrLoad(String cacheName, String key, Supplier<T> loader) {
        // PART 8 STEP 2:
        // Replace this method body with the snippet from the Part 8 guide.
        return loader.get();
    }
}
