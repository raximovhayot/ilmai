package org.aiincubator.ilmai.ai.embedding;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = GoogleGenAiEmbeddingProperties.PREFIX)
public class GoogleGenAiEmbeddingProperties {

    public static final String PREFIX = "ai.embedding";

    private String apiKey;

    private String model = "gemini-embedding-2";

    private int outputDimensionality = 768;

    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";

    private String documentTaskType = "RETRIEVAL_DOCUMENT";

    private String queryTaskType = "RETRIEVAL_QUERY";

    private Duration timeout = Duration.ofSeconds(30);

    private int maxBatchSize = 100;

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
