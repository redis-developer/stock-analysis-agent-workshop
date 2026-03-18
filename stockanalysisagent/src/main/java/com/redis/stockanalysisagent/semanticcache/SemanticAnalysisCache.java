package com.redis.stockanalysisagent.semanticcache;

import com.redis.vl.extensions.cache.SemanticCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import redis.clients.jedis.UnifiedJedis;

import java.util.Map;
import java.util.Optional;

@Service
public class SemanticAnalysisCache {

    private static final Logger log = LoggerFactory.getLogger(SemanticAnalysisCache.class);

    private final SemanticCache semanticCache;

    public SemanticAnalysisCache(
            SemanticCacheProperties properties,
            Optional<EmbeddingModel> embeddingModel,
            @org.springframework.beans.factory.annotation.Value("${spring.data.redis.host:localhost}") String redisHost,
            @org.springframework.beans.factory.annotation.Value("${spring.data.redis.port:6379}") int redisPort
    ) {
        this.semanticCache = initializeCache(properties, embeddingModel, redisHost, redisPort);
    }

    public Optional<String> findAnswer(String request) {
        if (semanticCache == null) {
            return Optional.empty();
        }

        String normalizedRequest = normalize(request);
        if (normalizedRequest.isEmpty()) {
            return Optional.empty();
        }

        try {
            return semanticCache.check(normalizedRequest)
                    .map(cacheHit -> {
                        log.info("Semantic cache hit for request at distance {}", cacheHit.getDistance());
                        return cacheHit.getResponse();
                    });
        } catch (RuntimeException ex) {
            log.warn("Semantic cache lookup failed.", ex);
            return Optional.empty();
        }
    }

    public void store(String request, String response) {
        if (semanticCache == null) {
            return;
        }

        String normalizedRequest = normalize(request);
        String normalizedResponse = normalize(response);
        if (normalizedRequest.isEmpty() || normalizedResponse.isEmpty()) {
            return;
        }

        try {
            semanticCache.store(
                    normalizedRequest,
                    normalizedResponse,
                    Map.of("kind", "stock-analysis")
            );
            log.info("Semantic cache stored response for request.");
        } catch (RuntimeException ex) {
            log.warn("Semantic cache store failed.", ex);
        }
    }

    @jakarta.annotation.PreDestroy
    void close() {
        if (semanticCache != null) {
            semanticCache.disconnect();
        }
    }

    private SemanticCache initializeCache(
            SemanticCacheProperties properties,
            Optional<EmbeddingModel> embeddingModel,
            String redisHost,
            int redisPort
    ) {
        if (!properties.isEnabled() || embeddingModel.isEmpty()) {
            return null;
        }

        try {
            UnifiedJedis redisClient = new UnifiedJedis("redis://%s:%s".formatted(redisHost, redisPort));
            return new SemanticCache.Builder()
                    .name(properties.getName())
                    .redisClient(redisClient)
                    .vectorizer(new SpringAiEmbeddingVectorizer(
                            properties.getEmbeddingModelName(),
                            embeddingModel.orElseThrow(),
                            properties.getEmbeddingDimensions()
                    ))
                    .distanceThreshold(properties.getDistanceThreshold())
                    .ttl(properties.getTtlSeconds())
                    .build();
        } catch (RuntimeException ex) {
            log.warn("Semantic cache is disabled because RedisVL initialization failed.", ex);
            return null;
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.trim().replaceAll("\\s+", " ");
    }
}
