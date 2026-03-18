package com.redis.stockanalysisagent.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    @ConditionalOnProperty(prefix = "spring.cache", name = "type", havingValue = "redis")
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        GenericJacksonJsonRedisSerializer valueSerializer = new GenericJacksonJsonRedisSerializer(
                JsonMapper.builder()
                        .findAndAddModules()
                        .build()
        );

        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer))
                .entryTtl(Duration.ofMinutes(10));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(Map.of(
                        CacheNames.MARKET_DATA_QUOTES, defaults.entryTtl(Duration.ofSeconds(30)),
                        CacheNames.TECHNICAL_ANALYSIS_SNAPSHOTS, defaults.entryTtl(Duration.ofMinutes(2)),
                        CacheNames.SEC_TICKER_INDEX, defaults.entryTtl(Duration.ofHours(24)),
                        CacheNames.SEC_COMPANY_FACTS, defaults.entryTtl(Duration.ofHours(12)),
                        CacheNames.SEC_SUBMISSIONS, defaults.entryTtl(Duration.ofMinutes(15)),
                        CacheNames.TAVILY_NEWS_SEARCH, defaults.entryTtl(Duration.ofHours(24))
                ))
                .build();
    }
}
