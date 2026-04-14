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

    private final SemanticCache semanticCache;

    public SemanticAnalysisCache(
            SemanticCacheProperties properties,
            EmbeddingModel embeddingModel,
            @Value("${spring.data.redis.host:localhost}") String redisHost,
            @Value("${spring.data.redis.port:6378}") int redisPort
    ) {
        this.semanticCache = initializeCache(properties, embeddingModel, redisHost, redisPort);
    }

    public Optional<String> findResponse(String request) {
        // PART 8 STEP 3:
        // Replace this method body with the advisor-based semantic cache lookup snippet
        // from the Part 8 guide.
        return Optional.empty();
    }

    public void storeResponse(String request, String response) {
        // PART 8 STEP 4:
        // Replace this method body with the semantic cache store snippet from the Part 8 guide.
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

    private SemanticCache initializeCache(
            SemanticCacheProperties properties,
            EmbeddingModel embeddingModel,
            String redisHost,
            int redisPort
    ) {
        // PART 8 STEP 5:
        // Replace this method body with the snippet from the Part 8 guide.
        return null;
    }

    private void ensureIndexExists(SearchIndex index, String cacheName) {
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
