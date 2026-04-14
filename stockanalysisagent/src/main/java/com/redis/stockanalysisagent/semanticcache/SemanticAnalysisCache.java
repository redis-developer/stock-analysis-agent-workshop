package com.redis.stockanalysisagent.semanticcache;

import com.redis.vl.extensions.cache.SemanticCache;
import com.redis.vl.index.SearchIndex;
import com.redis.vl.schema.IndexSchema;
import com.redis.vl.schema.VectorField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import redis.clients.jedis.UnifiedJedis;

import java.util.Map;
import java.util.Optional;

@Service
public class SemanticAnalysisCache {

    private static final Logger log = LoggerFactory.getLogger(SemanticAnalysisCache.class);

    private final String cacheName;
    private final SearchIndex index;
    private final SemanticCache semanticCache;

    public SemanticAnalysisCache(
            SemanticCacheProperties properties,
            EmbeddingModel embeddingModel,
            @Value("${spring.data.redis.host:localhost}") String redisHost,
            @Value("${spring.data.redis.port:6378}") int redisPort
    ) {
        this.cacheName = properties.getName();

        UnifiedJedis redisClient = new UnifiedJedis("redis://%s:%s".formatted(redisHost, redisPort));
        this.index = createIndex(properties, redisClient);
        ensureIndexExists();
        this.semanticCache = new SemanticCache.Builder()
                .name(properties.getName())
                .redisClient(redisClient)
                .vectorizer(new SpringAiEmbeddingVectorizer(
                        properties.getEmbeddingModelName(),
                        embeddingModel,
                        properties.getEmbeddingDimensions()
                ))
                .distanceThreshold(properties.getDistanceThreshold())
                .ttl(properties.getTtlSeconds())
                .build();
    }

    public Optional<String> findResponse(String request) {
        return semanticCache.check(request)
                .map(cacheHit -> {
                    log.info("Semantic cache hit for request at distance {}", cacheHit.getDistance());
                    return cacheHit.getResponse();
                });
    }

    public void storeResponse(String request, String response) {
        semanticCache.store(
                request,
                response,
                Map.of("kind", "stock-analysis")
        );
        log.info("Semantic cache stored response for request.");
    }

    public Optional<String> findAnswer(String request) {
        return findResponse(request);
    }

    public void store(String request, String response) {
        storeResponse(request, response);
    }

    private SearchIndex createIndex(SemanticCacheProperties properties, UnifiedJedis redisClient) {
        IndexSchema schema = IndexSchema.builder()
                .name(properties.getName())
                .prefix("cache:" + properties.getName() + ":")
                .storageType(IndexSchema.StorageType.HASH)
                .addTextField("prompt", field -> {
                })
                .addTextField("response", field -> {
                })
                .addVectorField("prompt_vector", properties.getEmbeddingDimensions(), field -> field
                        .algorithm(redis.clients.jedis.search.schemafields.VectorField.VectorAlgorithm.FLAT)
                        .distanceMetric(VectorField.DistanceMetric.COSINE))
                .addNumericField("inserted_at", field -> {
                })
                .addNumericField("updated_at", field -> {
                })
                .addTagField("user", field -> {
                })
                .addTagField("session", field -> {
                })
                .addTagField("category", field -> {
                })
                .build();

        return new SearchIndex(schema, redisClient);
    }

    private void ensureIndexExists() {
        if (index.exists()) {
            return;
        }

        index.create();

        if (!index.exists()) {
            throw new IllegalStateException("Semantic cache index %s was not created.".formatted(cacheName));
        }

        log.info("Created semantic cache index {}", cacheName);
    }
}
