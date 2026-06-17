package org.aiincubator.ilmai.ai.ingestion;

public class EmbeddingUnavailableException extends RuntimeException {

    public EmbeddingUnavailableException() {
        super("Embedding service is not available; the VectorStore bean is not registered.");
    }
}
