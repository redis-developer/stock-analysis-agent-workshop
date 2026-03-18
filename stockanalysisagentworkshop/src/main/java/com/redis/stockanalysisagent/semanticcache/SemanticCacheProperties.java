package com.redis.stockanalysisagent.semanticcache;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "stock-analysis.semantic-cache")
public class SemanticCacheProperties {

    private boolean enabled = true;
    private float distanceThreshold = 0.12f;
    private int ttlSeconds = 300;
    private String name = "stock-analysis-semantic-cache";
    private String embeddingModelName = "text-embedding-3-small";
    private int embeddingDimensions = 1536;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public float getDistanceThreshold() {
        return distanceThreshold;
    }

    public void setDistanceThreshold(float distanceThreshold) {
        this.distanceThreshold = distanceThreshold;
    }

    public int getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(int ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmbeddingModelName() {
        return embeddingModelName;
    }

    public void setEmbeddingModelName(String embeddingModelName) {
        this.embeddingModelName = embeddingModelName;
    }

    public int getEmbeddingDimensions() {
        return embeddingDimensions;
    }

    public void setEmbeddingDimensions(int embeddingDimensions) {
        this.embeddingDimensions = embeddingDimensions;
    }
}
