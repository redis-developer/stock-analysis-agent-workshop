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
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return loader.get();
        }

        Cache.ValueWrapper cached = cache.get(key);
        if (cached != null && cached.get() != null) {
            return (T) cached.get();
        }

        String lockKey = cacheName + "::" + key;
        Object lock = locks.computeIfAbsent(lockKey, ignored -> new Object());
        synchronized (lock) {
            try {
                Cache.ValueWrapper doubleChecked = cache.get(key);
                if (doubleChecked != null && doubleChecked.get() != null) {
                    return (T) doubleChecked.get();
                }

                T loaded = loader.get();
                if (loaded != null) {
                    cache.put(key, loaded);
                }
                return loaded;
            } finally {
                locks.remove(lockKey, lock);
            }
        }
    }
}
