package org.aiincubator.ilmai.ai;

import org.aiincubator.ilmai.ai.embedding.MultimodalContent;

public interface MultimodalEmbeddingApi {

    boolean isAvailable();

    float[] embed(MultimodalContent content);
}
