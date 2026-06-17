package org.aiincubator.ilmai.ai.embedding;

import com.google.genai.errors.ClientException;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleGenAiEmbeddingModelTest {

    private GoogleGenAiEmbeddingProperties enabledProperties() {
        GoogleGenAiEmbeddingProperties p = new GoogleGenAiEmbeddingProperties();
        p.setApiKey("test-key");
        p.setModel("gemini-embedding-2");
        p.setOutputDimensionality(4);
        p.setBaseUrl("https://example.invalid");
        return p;
    }

    private EmbedContentResponse responseWith(float[]... vectors) {
        List<ContentEmbedding> embeddings = new ArrayList<>(vectors.length);
        for (float[] v : vectors) {
            List<Float> values = new ArrayList<>(v.length);
            for (float f : v) {
                values.add(f);
            }
            embeddings.add(ContentEmbedding.builder().values(values).build());
        }
        return EmbedContentResponse.builder().embeddings(embeddings).build();
    }

    private static final class RecordingInvoker implements EmbedContentInvoker {
        String calledModel;
        List<String> calledInputs;
        EmbedContentConfig calledConfig;
        EmbedContentResponse response;
        RuntimeException error;

        @Override
        public EmbedContentResponse embedContent(String model, List<String> inputs, EmbedContentConfig config) {
            this.calledModel = model;
            this.calledInputs = inputs;
            this.calledConfig = config;
            if (error != null) {
                throw error;
            }
            return response;
        }
    }

    @Test
    void embedString_invokesSdkWithQueryTaskType() {
        GoogleGenAiEmbeddingProperties props = enabledProperties();
        RecordingInvoker invoker = new RecordingInvoker();
        invoker.response = responseWith(new float[]{0.1f, 0.2f, 0.3f, 0.4f});

        GoogleGenAiEmbeddingModel model = new GoogleGenAiEmbeddingModel(props, invoker);
        float[] result = model.embed("what is dns");

        assertThat(result).containsExactly(0.1f, 0.2f, 0.3f, 0.4f);
        assertThat(invoker.calledModel).isEqualTo("gemini-embedding-2");
        assertThat(invoker.calledInputs).containsExactly("what is dns");
        assertThat(invoker.calledConfig.taskType()).contains("RETRIEVAL_QUERY");
        assertThat(invoker.calledConfig.outputDimensionality()).contains(4);
    }

    @Test
    void embedDocument_usesDocumentTaskType() {
        GoogleGenAiEmbeddingProperties props = enabledProperties();
        RecordingInvoker invoker = new RecordingInvoker();
        invoker.response = responseWith(new float[]{1.0f, 0.0f, 0.0f, 0.0f});

        GoogleGenAiEmbeddingModel model = new GoogleGenAiEmbeddingModel(props, invoker);
        float[] result = model.embed(new Document("hello world"));

        assertThat(result).containsExactly(1.0f, 0.0f, 0.0f, 0.0f);
        assertThat(invoker.calledInputs).containsExactly("hello world");
        assertThat(invoker.calledConfig.taskType()).contains("RETRIEVAL_DOCUMENT");
    }

    @Test
    void callWithMultipleInputs_passesAllInputsInOneSdkCall() {
        GoogleGenAiEmbeddingProperties props = enabledProperties();
        RecordingInvoker invoker = new RecordingInvoker();
        invoker.response = responseWith(
                new float[]{0.1f, 0.2f, 0.3f, 0.4f},
                new float[]{0.5f, 0.6f, 0.7f, 0.8f});

        GoogleGenAiEmbeddingModel model = new GoogleGenAiEmbeddingModel(props, invoker);
        EmbeddingResponse response = model.call(new EmbeddingRequest(List.of("a", "b"), null));

        assertThat(response.getResults()).hasSize(2);
        assertThat(response.getResults().get(0).getOutput()).containsExactly(0.1f, 0.2f, 0.3f, 0.4f);
        assertThat(response.getResults().get(1).getOutput()).containsExactly(0.5f, 0.6f, 0.7f, 0.8f);
        assertThat(response.getResults().get(0).getIndex()).isEqualTo(0);
        assertThat(response.getResults().get(1).getIndex()).isEqualTo(1);
        assertThat(invoker.calledInputs).containsExactly("a", "b");
        assertThat(invoker.calledConfig.taskType()).contains("RETRIEVAL_DOCUMENT");
    }

    @Test
    void callWithMoreThanBatchSize_splitsIntoMultipleSdkCalls() {
        GoogleGenAiEmbeddingProperties props = enabledProperties();
        props.setMaxBatchSize(2);
        List<EmbedContentResponse> responses = List.of(
                responseWith(new float[]{1, 0, 0, 0}, new float[]{0, 1, 0, 0}),
                responseWith(new float[]{0, 0, 1, 0}));
        List<List<String>> capturedBatches = new ArrayList<>();
        EmbedContentInvoker invoker = new EmbedContentInvoker() {
            int call = 0;

            @Override
            public EmbedContentResponse embedContent(String model, List<String> inputs, EmbedContentConfig config) {
                capturedBatches.add(List.copyOf(inputs));
                return responses.get(call++);
            }
        };

        GoogleGenAiEmbeddingModel model = new GoogleGenAiEmbeddingModel(props, invoker);
        EmbeddingResponse response = model.call(new EmbeddingRequest(List.of("a", "b", "c"), null));

        assertThat(response.getResults()).hasSize(3);
        assertThat(capturedBatches).containsExactly(List.of("a", "b"), List.of("c"));
        assertThat(response.getResults().get(2).getIndex()).isEqualTo(2);
    }

    @Test
    void googleGenAiEmbeddingOptions_overridesTaskTypeModelAndDimensions() {
        GoogleGenAiEmbeddingProperties props = enabledProperties();
        RecordingInvoker invoker = new RecordingInvoker();
        invoker.response = responseWith(new float[]{0, 0, 0, 0, 0, 0, 0, 1});

        GoogleGenAiEmbeddingModel model = new GoogleGenAiEmbeddingModel(props, invoker);
        EmbeddingOptions options = GoogleGenAiEmbeddingOptions.of("text-embedding-005", 8, "SEMANTIC_SIMILARITY");
        EmbeddingResponse response = model.call(new EmbeddingRequest(List.of("x"), options));

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).getOutput()).hasSize(8);
        assertThat(invoker.calledModel).isEqualTo("text-embedding-005");
        assertThat(invoker.calledConfig.taskType()).contains("SEMANTIC_SIMILARITY");
        assertThat(invoker.calledConfig.outputDimensionality()).contains(8);
    }

    @Test
    void sdkExceptionIsWrappedAsEmbeddingApiException() {
        GoogleGenAiEmbeddingProperties props = enabledProperties();
        RecordingInvoker invoker = new RecordingInvoker();
        invoker.error = new ClientException(500, "Internal Server Error", "boom");

        GoogleGenAiEmbeddingModel model = new GoogleGenAiEmbeddingModel(props, invoker);
        assertThatThrownBy(() -> model.embed("hi"))
                .isInstanceOf(EmbeddingApiException.class)
                .hasMessageContaining("500");
    }

    @Test
    void runtimeExceptionIsWrappedAsEmbeddingApiException() {
        GoogleGenAiEmbeddingProperties props = enabledProperties();
        RecordingInvoker invoker = new RecordingInvoker();
        invoker.error = new RuntimeException("network down");

        GoogleGenAiEmbeddingModel model = new GoogleGenAiEmbeddingModel(props, invoker);
        assertThatThrownBy(() -> model.embed("hi"))
                .isInstanceOf(EmbeddingApiException.class)
                .hasMessageContaining("Google GenAI embedding failed");
    }

    @Test
    void missingApiKeyThrowsIllegalState() {
        GoogleGenAiEmbeddingProperties props = new GoogleGenAiEmbeddingProperties();
        props.setApiKey("  ");
        GoogleGenAiEmbeddingModel model = new GoogleGenAiEmbeddingModel(props, new RecordingInvoker());

        assertThatThrownBy(() -> model.call(new EmbeddingRequest(List.of("a"), null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("api-key");
    }

    @Test
    void dimensionsReturnsConfiguredOutputDimensionality() {
        GoogleGenAiEmbeddingProperties props = enabledProperties();
        props.setOutputDimensionality(768);
        GoogleGenAiEmbeddingModel model = new GoogleGenAiEmbeddingModel(props, new RecordingInvoker());
        assertThat(model.dimensions()).isEqualTo(768);
    }

    @Test
    void responseWithNoEmbeddingsThrows() {
        GoogleGenAiEmbeddingProperties props = enabledProperties();
        RecordingInvoker invoker = new RecordingInvoker();
        invoker.response = EmbedContentResponse.builder().build();

        GoogleGenAiEmbeddingModel model = new GoogleGenAiEmbeddingModel(props, invoker);
        assertThatThrownBy(() -> model.embed("hi"))
                .isInstanceOf(EmbeddingApiException.class)
                .hasMessageContaining("missing 'embeddings'");
    }

    @Test
    void responseWithMismatchedVectorCountThrows() {
        GoogleGenAiEmbeddingProperties props = enabledProperties();
        RecordingInvoker invoker = new RecordingInvoker();
        invoker.response = responseWith(new float[]{0.1f, 0.2f, 0.3f, 0.4f});

        GoogleGenAiEmbeddingModel model = new GoogleGenAiEmbeddingModel(props, invoker);
        assertThatThrownBy(() -> model.call(new EmbeddingRequest(List.of("a", "b"), null)))
                .isInstanceOf(EmbeddingApiException.class)
                .hasMessageContaining("1 vectors for 2 inputs");
    }

    @Test
    void emptyRequestReturnsEmptyResponse() {
        GoogleGenAiEmbeddingProperties props = enabledProperties();
        GoogleGenAiEmbeddingModel model = new GoogleGenAiEmbeddingModel(props, new RecordingInvoker());

        EmbeddingResponse response = model.call(new EmbeddingRequest(List.of(), null));

        assertThat(response.getResults()).isEmpty();
    }

    @Test
    void contentEmbeddingWithMissingValuesThrows() {
        GoogleGenAiEmbeddingProperties props = enabledProperties();
        RecordingInvoker invoker = new RecordingInvoker();
        ContentEmbedding empty = ContentEmbedding.builder().build();
        invoker.response = EmbedContentResponse.builder().embeddings(List.of(empty)).build();

        GoogleGenAiEmbeddingModel model = new GoogleGenAiEmbeddingModel(props, invoker);
        assertThatThrownBy(() -> model.embed("hi"))
                .isInstanceOf(EmbeddingApiException.class)
                .hasMessageContaining("missing 'values'");
    }

}
