package org.aiincubator.ilmai.ai.ingestion.support;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BagOfWordsTestEmbeddingModel extends AbstractEmbeddingModel {

    public static final int DIMENSIONS = 768;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> inputs = request.getInstructions();
        List<Embedding> embeddings = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            embeddings.add(new Embedding(embed(inputs.get(i)), i));
        }
        return new EmbeddingResponse(embeddings, new EmbeddingResponseMetadata("bag-of-words-test", null));
    }

    @Override
    public float[] embed(Document document) {
        if (document == null) {
            return new float[DIMENSIONS];
        }
        return embed(document.getText());
    }

    @Override
    public float[] embed(String text) {
        float[] vec = new float[DIMENSIONS];
        if (text == null || text.isBlank()) {
            return vec;
        }
        String[] tokens = text.toLowerCase(Locale.ROOT).split("\\W+");
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            int dim = Math.floorMod(token.hashCode(), DIMENSIONS);
            vec[dim] += 1.0f;
        }
        double sumSquares = 0.0;
        for (float v : vec) {
            sumSquares += v * v;
        }
        if (sumSquares > 0.0) {
            float norm = (float) Math.sqrt(sumSquares);
            for (int i = 0; i < DIMENSIONS; i++) {
                vec[i] /= norm;
            }
        }
        return vec;
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }
}
