package com.redis.stockanalysisagent.semanticcache;

import com.redis.vl.utils.vectorize.BaseVectorizer;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

public class SpringAiEmbeddingVectorizer extends BaseVectorizer {

    private final EmbeddingModel embeddingModel;

    public SpringAiEmbeddingVectorizer(String modelName, EmbeddingModel embeddingModel, int dimensions) {
        super(modelName, dimensions, "float32");
        this.embeddingModel = embeddingModel;
    }

    @Override
    protected float[] generateEmbedding(String text) {
        return embeddingModel.embed(text);
    }

    @Override
    protected List<float[]> generateEmbeddingsBatch(List<String> texts, int batchSize) {
        return embeddingModel.embed(texts);
    }

    @Override
    public String getType() {
        return "spring-ai";
    }
}
