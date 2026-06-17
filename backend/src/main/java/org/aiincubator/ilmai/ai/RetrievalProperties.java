package org.aiincubator.ilmai.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "ai.retrieval")
public class RetrievalProperties {

    private int topK = 6;

    private double similarityThreshold = 0.0d;
}
