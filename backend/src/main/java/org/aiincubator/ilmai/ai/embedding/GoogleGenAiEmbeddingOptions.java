package org.aiincubator.ilmai.ai.embedding;

import lombok.Getter;
import lombok.Setter;
import org.springframework.ai.embedding.EmbeddingOptions;

@Getter
@Setter
public class GoogleGenAiEmbeddingOptions implements EmbeddingOptions {

    private String model;

    private Integer dimensions;

    private String taskType;

    public static GoogleGenAiEmbeddingOptions of(String model, Integer dimensions, String taskType) {
        GoogleGenAiEmbeddingOptions o = new GoogleGenAiEmbeddingOptions();
        o.setModel(model);
        o.setDimensions(dimensions);
        o.setTaskType(taskType);
        return o;
    }
}
