package org.aiincubator.ilmai.ai.embedding;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(EmbeddingConfig.class);

    @Test
    void embeddingModelBeanAbsentWhenApiKeyMissing() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(EmbeddingModel.class));
    }

    @Test
    void embeddingModelBeanAbsentWhenApiKeyBlank() {
        contextRunner.withPropertyValues("ai.embedding.api-key=   ").run(context ->
                assertThat(context).doesNotHaveBean(EmbeddingModel.class));
    }

    @Test
    void embeddingModelBeanPresentWhenApiKeyConfigured() {
        contextRunner.withPropertyValues(
                "ai.embedding.api-key=test-key",
                "ai.embedding.base-url=https://example.invalid",
                "ai.embedding.model=gemini-embedding-2",
                "ai.embedding.output-dimensionality=768"
        ).run(context -> {
            assertThat(context).hasSingleBean(EmbeddingModel.class);
            assertThat(context).hasSingleBean(GoogleGenAiEmbeddingProperties.class);
            GoogleGenAiEmbeddingProperties props = context.getBean(GoogleGenAiEmbeddingProperties.class);
            assertThat(props.getApiKey()).isEqualTo("test-key");
            assertThat(props.getModel()).isEqualTo("gemini-embedding-2");
            assertThat(props.getOutputDimensionality()).isEqualTo(768);
            assertThat(context.getBean(EmbeddingModel.class).dimensions()).isEqualTo(768);
        });
    }
}
