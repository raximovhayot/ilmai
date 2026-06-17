package org.aiincubator.ilmai.ai.embedding;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.Blob;
import com.google.genai.types.Content;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.Part;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class GoogleGenAiEmbeddingModel extends AbstractEmbeddingModel {

    private final GoogleGenAiEmbeddingProperties properties;
    private final EmbedContentInvoker invoker;
    private final MultimodalEmbedContentInvoker multimodalInvoker;

    public GoogleGenAiEmbeddingModel(GoogleGenAiEmbeddingProperties properties) {
        this(properties, buildClient(properties));
    }

    private GoogleGenAiEmbeddingModel(GoogleGenAiEmbeddingProperties properties, Client client) {
        this(properties,
                (model, inputs, config) -> client.models.embedContent(model, inputs, config),
                (model, content, config) -> client.models.embedContent(model, content, config));
    }

    GoogleGenAiEmbeddingModel(GoogleGenAiEmbeddingProperties properties,
                              EmbedContentInvoker invoker,
                              MultimodalEmbedContentInvoker multimodalInvoker) {
        this.properties = properties;
        this.invoker = invoker;
        this.multimodalInvoker = multimodalInvoker;
    }

    GoogleGenAiEmbeddingModel(GoogleGenAiEmbeddingProperties properties, EmbedContentInvoker invoker) {
        this(properties, invoker, (model, content, config) -> {
            throw new EmbeddingApiException("multimodal invoker not configured", null);
        });
    }

    @Override
    public @NonNull EmbeddingResponse call(@NonNull EmbeddingRequest request) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Google GenAI embedding model is not configured (missing api-key)");
        }
        List<String> inputs = request.getInstructions();
        if (inputs.isEmpty()) {
            return new EmbeddingResponse(List.of(), new EmbeddingResponseMetadata());
        }
        String model = resolveModel(request.getOptions());
        Integer dimensions = resolveDimensions(request.getOptions());
        String taskType = resolveTaskType(request.getOptions(), properties.getDocumentTaskType());
        return embedBatched(inputs, model, dimensions, taskType);
    }

    @Override
    public float[] embed(Document document) {
        if (document == null) {
            return new float[0];
        }
        String text = getEmbeddingContent(document);
        List<Embedding> results = embedBatched(List.of(text),
                properties.getModel(),
                properties.getOutputDimensionality(),
                properties.getDocumentTaskType()).getResults();
        return results.isEmpty() ? new float[0] : results.get(0).getOutput();
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isEmpty()) {
            return new float[0];
        }
        List<Embedding> results = embedBatched(List.of(text),
                properties.getModel(),
                properties.getOutputDimensionality(),
                properties.getQueryTaskType()).getResults();
        return results.isEmpty() ? new float[0] : results.get(0).getOutput();
    }

    @Override
    public int dimensions() {
        return properties.getOutputDimensionality();
    }

    public float[] embedMultimodal(MultimodalContent content) {
        if (content == null || content.getParts() == null || content.getParts().isEmpty()) {
            return new float[0];
        }
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Google GenAI embedding model is not configured (missing api-key)");
        }
        String model = properties.getModel();
        Integer dimensions = properties.getOutputDimensionality();
        String taskType = content.getTaskType() != null && !content.getTaskType().isBlank()
                ? content.getTaskType()
                : properties.getDocumentTaskType();
        return callMultimodal(content, model, dimensions, taskType);
    }

    private float[] callMultimodal(MultimodalContent content, String model, Integer dimensions, String taskType) {
        EmbedContentConfig.Builder configBuilder = EmbedContentConfig.builder();
        if (taskType != null && !taskType.isBlank()) {
            configBuilder.taskType(taskType);
        }
        if (dimensions != null && dimensions > 0) {
            configBuilder.outputDimensionality(dimensions);
        }
        EmbedContentConfig config = configBuilder.build();
        Content sdkContent = toSdkContent(content.getParts());
        try {
            EmbedContentResponse response = multimodalInvoker.embedContent(model, sdkContent, config);
            List<float[]> vectors = extractVectors(response, 1);
            if (vectors.isEmpty()) {
                throw new EmbeddingApiException("Google GenAI multimodal embedding returned no vectors", null);
            }
            return vectors.get(0);
        } catch (ApiException ex) {
            log.warn("Google GenAI multimodal embedding call failed: code={} status={} message={}",
                    ex.code(), ex.status(), ex.message());
            throw new EmbeddingApiException(
                    "Google GenAI multimodal embedding failed: " + ex.code() + " " + ex.status(), ex);
        } catch (EmbeddingApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Google GenAI multimodal embedding call failed: {}", ex.toString());
            throw new EmbeddingApiException("Google GenAI multimodal embedding failed: " + ex.getMessage(), ex);
        }
    }

    private Content toSdkContent(List<MultimodalPart> parts) {
        List<Part> sdkParts = new ArrayList<>(parts.size());
        for (MultimodalPart part : parts) {
            sdkParts.add(toSdkPart(part));
        }
        return Content.builder().parts(sdkParts).build();
    }

    private Part toSdkPart(MultimodalPart part) {
        if (part instanceof TextPart text) {
            return Part.fromText(text.getText() == null ? "" : text.getText());
        }
        if (part instanceof InlineDataPart inline) {
            Blob blob = Blob.builder()
                    .data(inline.getData() == null ? new byte[0] : inline.getData())
                    .mimeType(inline.getMimeType() == null ? "" : inline.getMimeType())
                    .build();
            return Part.builder().inlineData(blob).build();
        }
        throw new EmbeddingApiException("Unsupported MultimodalPart: " + part.getClass().getName(), null);
    }

    private EmbeddingResponse embedBatched(List<String> inputs, String model, Integer dimensions, String taskType) {
        List<Embedding> all = new ArrayList<>(inputs.size());
        int batchSize = Math.max(1, properties.getMaxBatchSize());
        int globalIndex = 0;
        for (int from = 0; from < inputs.size(); from += batchSize) {
            int to = Math.min(from + batchSize, inputs.size());
            List<String> batch = inputs.subList(from, to);
            List<float[]> vectors = callBatch(batch, model, dimensions, taskType);
            if (vectors.size() != batch.size()) {
                throw new EmbeddingApiException(
                        "Google GenAI embedding returned " + vectors.size() + " vectors for " + batch.size() + " inputs",
                        null);
            }
            for (float[] v : vectors) {
                all.add(new Embedding(v, globalIndex++));
            }
        }
        return new EmbeddingResponse(all, new EmbeddingResponseMetadata(model, null));
    }

    private List<float[]> callBatch(List<String> inputs, String model, Integer dimensions, String taskType) {
        EmbedContentConfig.Builder configBuilder = EmbedContentConfig.builder();
        if (taskType != null && !taskType.isBlank()) {
            configBuilder.taskType(taskType);
        }
        if (dimensions != null && dimensions > 0) {
            configBuilder.outputDimensionality(dimensions);
        }
        EmbedContentConfig config = configBuilder.build();
        try {
            EmbedContentResponse response = invoker.embedContent(model, inputs, config);
            return extractVectors(response, inputs.size());
        } catch (ApiException ex) {
            log.warn("Google GenAI embedding call failed: code={} status={} message={}",
                    ex.code(), ex.status(), ex.message());
            throw new EmbeddingApiException(
                    "Google GenAI embedding failed: " + ex.code() + " " + ex.status(), ex);
        } catch (RuntimeException ex) {
            log.warn("Google GenAI embedding call failed: {}", ex.toString());
            throw new EmbeddingApiException("Google GenAI embedding failed: " + ex.getMessage(), ex);
        }
    }

    private List<float[]> extractVectors(EmbedContentResponse response, int expectedCount) {
        if (response == null) {
            throw new EmbeddingApiException(
                    "Google GenAI embedding response was null (expected " + expectedCount + ")", null);
        }
        Optional<List<ContentEmbedding>> embeddings = response.embeddings();
        if (embeddings.isEmpty()) {
            throw new EmbeddingApiException(
                    "Google GenAI embedding response missing 'embeddings' (expected " + expectedCount + ")", null);
        }
        List<ContentEmbedding> list = embeddings.get();
        List<float[]> out = new ArrayList<>(list.size());
        for (ContentEmbedding ce : list) {
            out.add(toFloatArray(ce));
        }
        return out;
    }

    private float[] toFloatArray(ContentEmbedding embedding) {
        Optional<List<Float>> values = embedding.values();
        if (values.isEmpty()) {
            throw new EmbeddingApiException("Google GenAI embedding response missing 'values' array", null);
        }
        List<Float> list = values.get();
        float[] result = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Float f = list.get(i);
            if (f == null) {
                throw new EmbeddingApiException("Google GenAI embedding response 'values' contained null entry", null);
            }
            result[i] = f;
        }
        return result;
    }

    private String resolveModel(EmbeddingOptions options) {
        if (options != null && options.getModel() != null && !options.getModel().isBlank()) {
            return options.getModel();
        }
        return properties.getModel();
    }

    private Integer resolveDimensions(EmbeddingOptions options) {
        if (options != null && options.getDimensions() != null && options.getDimensions() > 0) {
            return options.getDimensions();
        }
        return properties.getOutputDimensionality();
    }

    private String resolveTaskType(EmbeddingOptions options, String fallback) {
        if (options instanceof GoogleGenAiEmbeddingOptions opts
                && opts.getTaskType() != null && !opts.getTaskType().isBlank()) {
            return opts.getTaskType();
        }
        return fallback;
    }

    private static Client buildClient(GoogleGenAiEmbeddingProperties properties) {
        Client.Builder builder = Client.builder().apiKey(properties.getApiKey());
        HttpOptions.Builder httpOptions = HttpOptions.builder();
        boolean hasHttpOptions = false;
        String configuredBaseUrl = properties.getBaseUrl();
        String resolvedBaseUrl = null;
        String resolvedApiVersion = null;
        if (configuredBaseUrl != null && !configuredBaseUrl.isBlank()) {
            String trimmed = configuredBaseUrl.trim();
            while (trimmed.endsWith("/")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            try {
                URI uri = new URI(trimmed);
                String path = uri.getPath();
                if (path != null && !path.isEmpty() && !path.equals("/")) {
                    resolvedApiVersion = path.startsWith("/") ? path.substring(1) : path;
                    resolvedBaseUrl = new URI(uri.getScheme(), uri.getAuthority(), null, null, null).toString();
                } else {
                    resolvedBaseUrl = trimmed;
                }
            } catch (URISyntaxException ex) {
                resolvedBaseUrl = trimmed;
            }
        }
        if (resolvedBaseUrl != null) {
            httpOptions.baseUrl(resolvedBaseUrl);
            hasHttpOptions = true;
        }
        if (resolvedApiVersion != null) {
            httpOptions.apiVersion(resolvedApiVersion);
            hasHttpOptions = true;
        }
        if (properties.getTimeout() != null) {
            long millis = properties.getTimeout().toMillis();
            if (millis > 0 && millis <= Integer.MAX_VALUE) {
                httpOptions.timeout((int) millis);
                hasHttpOptions = true;
            }
        }
        if (hasHttpOptions) {
            builder.httpOptions(httpOptions.build());
        }
        return builder.build();
    }
}
