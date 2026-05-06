package com.redis.stockanalysisagent.semanticguardrail;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "stock-analysis.semantic-guardrail")
public class SemanticGuardrailProperties {

    private boolean enabled = true;
    private float distanceThreshold = 0.5f;
    private String name = "stock-analysis-semantic-guardrail";
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
