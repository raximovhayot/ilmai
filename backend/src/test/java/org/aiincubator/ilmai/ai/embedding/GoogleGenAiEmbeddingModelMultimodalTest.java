package org.aiincubator.ilmai.ai.embedding;

import com.google.genai.errors.ClientException;
import com.google.genai.types.Content;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import com.google.genai.types.Part;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleGenAiEmbeddingModelMultimodalTest {

    private GoogleGenAiEmbeddingProperties enabledProperties() {
        GoogleGenAiEmbeddingProperties p = new GoogleGenAiEmbeddingProperties();
        p.setApiKey("test-key");
        p.setModel("gemini-embedding-2");
        p.setOutputDimensionality(4);
        p.setBaseUrl("https://example.invalid");
        return p;
    }

    private EmbedContentResponse singleVectorResponse(float... vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float f : vector) {
            values.add(f);
        }
        return EmbedContentResponse.builder()
                .embeddings(List.of(ContentEmbedding.builder().values(values).build()))
                .build();
    }

    private static final class NoopTextInvoker implements EmbedContentInvoker {
        @Override
        public EmbedContentResponse embedContent(String model, List<String> inputs, EmbedContentConfig config) {
            throw new AssertionError("text invoker should not be called in multimodal tests");
        }
    }

    private static final class RecordingMultimodalInvoker implements MultimodalEmbedContentInvoker {
        String calledModel;
        Content calledContent;
        EmbedContentConfig calledConfig;
        int callCount;
        List<EmbedContentResponse> responses = new ArrayList<>();
        RuntimeException error;

        @Override
        public EmbedContentResponse embedContent(String model, Content content, EmbedContentConfig config) {
            this.calledModel = model;
            this.calledContent = content;
            this.calledConfig = config;
            if (error != null) {
                throw error;
            }
            EmbedContentResponse r = responses.get(callCount);
            callCount++;
            return r;
        }
    }

    @Test
    void embedMultimodal_textOnlyPart_sendsTextPart() {
        GoogleGenAiEmbeddingProperties props = enabledProperties();
        RecordingMultimodalInvoker invoker = new RecordingMultimodalInvoker();
        invoker.responses.add(singleVectorResponse(0.1f, 0.2f, 0.3f, 0.4f));

        GoogleGenAiEmbeddingModel model = new GoogleGenAiEmbeddingModel(props, new NoopTextInvoker(), invoker);

        MultimodalContent content = new MultimodalContent(List.<MultimodalPart>of(new TextPart("hello")));
        float[] result = model.embedMultimodal(content);

        assertThat(result).containsExactly(0.1f, 0.2f, 0.3f, 0.4f);
        assertThat(invoker.calledModel).isEqualTo("gemini-embedding-2");
        assertThat(invoker.calledConfig.taskType()).contains("RETRIEVAL_DOCUMENT");
        assertThat(invoker.calledConfig.outputDimensionality()).contains(4);
        List<Part> parts = invoker.calledContent.parts().orElseThrow();
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).text()).contains("hello");
    }

    @Test
    void embedMultimodal_inlineDataPart_sendsInlineBlob() {
        GoogleGenAiEmbeddingProperties props = enabledProperties();
        RecordingMultimodalInvoker invoker = new RecordingMultimodalInvoker();
        invoker.responses.add(singleVectorResponse(1.0f, 0.0f, 0.0f, 0.0f));

        GoogleGenAiEmbeddingModel model = new GoogleGenAiEmbeddingModel(props, new NoopTextInvoker(), invoker);

        byte[] imageBytes = new byte[]{1, 2, 3, 4};
        MultimodalContent content = new MultimodalContent(List.<MultimodalPart>of(
                new TextPart("describe"),
                new InlineDataPart("image/png", imageBytes)));
        float[] result = model.embedMultimodal(content);

        assertThat(result).containsExactly(1.0f, 0.0f, 0.0f, 0.0f);
        List<Part> parts = invoker.calledContent.parts().orElseThrow();
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).text()).contains("describe");
        Optional<byte[]> data = parts.get(1).inlineData().orElseThrow().data();
        assertThat(data).isPresent();
        assertThat(data.get()).containsExactly(1, 2, 3, 4);
        assertThat(parts.get(1).inlineData().orElseThrow().mimeType()).contains("image/png");
    }

    @Test
    void embedMultimodal_taskTypeOverride_overridesDefault() {
        GoogleGenAiEmbeddingProperties props = enabledProperties();
        RecordingMultimodalInvoker invoker = new RecordingMultimodalInvoker();
        invoker.responses.add(singleVectorResponse(0.1f, 0.2f, 0.3f, 0.4f));

        GoogleGenAiEmbeddingModel model = new GoogleGenAiEmbeddingModel(props, new NoopTextInvoker(), invoker);

        MultimodalContent content = new MultimodalContent(
                List.<MultimodalPart>of(new TextPart("q")), "RETRIEVAL_QUERY");
        model.embedMultimodal(content);

        assertThat(invoker.calledConfig.taskType()).contains("RETRIEVAL_QUERY");
    }

    @Test
    void embedMultimodal_nullOrEmpty_returnsEmptyArray() {
        GoogleGenAiEmbeddingProperties props = enabledProperties();
        RecordingMultimodalInvoker invoker = new RecordingMultimodalInvoker();

        GoogleGenAiEmbeddingModel model = new GoogleGenAiEmbeddingModel(props, new NoopTextInvoker(), invoker);

        assertThat(model.embedMultimodal(null)).isEmpty();
        assertThat(model.embedMultimodal(new MultimodalContent(List.of()))).isEmpty();
        assertThat(invoker.callCount).isZero();
    }

    @Test
    void embedMultimodal_sdkExceptionIsWrapped() {
        GoogleGenAiEmbeddingProperties props = enabledProperties();
        RecordingMultimodalInvoker invoker = new RecordingMultimodalInvoker();
        invoker.error = new ClientException(429, "RESOURCE_EXHAUSTED", "rate limited");

        GoogleGenAiEmbeddingModel model = new GoogleGenAiEmbeddingModel(props, new NoopTextInvoker(), invoker);

        assertThatThrownBy(() -> model.embedMultimodal(
                new MultimodalContent(List.<MultimodalPart>of(new TextPart("x")))))
                .isInstanceOf(EmbeddingApiException.class)
                .hasMessageContaining("429");
    }

    @Test
    void embedMultimodal_missingApiKeyThrowsIllegalState() {
        GoogleGenAiEmbeddingProperties props = enabledProperties();
        props.setApiKey(null);

        GoogleGenAiEmbeddingModel model = new GoogleGenAiEmbeddingModel(
                props, new NoopTextInvoker(), new RecordingMultimodalInvoker());

        assertThatThrownBy(() -> model.embedMultimodal(
                new MultimodalContent(List.<MultimodalPart>of(new TextPart("x")))))
                .isInstanceOf(IllegalStateException.class);
    }
}
