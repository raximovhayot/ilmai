package org.aiincubator.ilmai.ai.embedding.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.ai.MultimodalEmbeddingApi;
import org.aiincubator.ilmai.ai.embedding.GoogleGenAiEmbeddingModel;
import org.aiincubator.ilmai.ai.embedding.MultimodalContent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultMultimodalEmbeddingApi implements MultimodalEmbeddingApi {

    private final ObjectProvider<GoogleGenAiEmbeddingModel> modelProvider;

    @Override
    public boolean isAvailable() {
        return modelProvider.getIfAvailable() != null;
    }

    @Override
    public float[] embed(MultimodalContent content) {
        return requireModel().embedMultimodal(content);
    }

    private GoogleGenAiEmbeddingModel requireModel() {
        GoogleGenAiEmbeddingModel model = modelProvider.getIfAvailable();
        if (model == null) {
            throw new IllegalStateException("Google GenAI embedding model is not configured (missing api-key)");
        }
        return model;
    }
}
