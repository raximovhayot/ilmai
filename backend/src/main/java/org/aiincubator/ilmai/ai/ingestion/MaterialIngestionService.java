package org.aiincubator.ilmai.ai.ingestion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.ai.MaterialIngestionCompletedEvent;
import org.aiincubator.ilmai.ai.MultimodalEmbeddingApi;
import org.aiincubator.ilmai.ai.embedding.InlineDataPart;
import org.aiincubator.ilmai.ai.embedding.MultimodalContent;
import org.aiincubator.ilmai.ai.embedding.MultimodalPart;
import org.aiincubator.ilmai.ai.ingestion.reader.AudioSegmentPart;
import org.aiincubator.ilmai.ai.ingestion.reader.ImagePart;
import org.aiincubator.ilmai.ai.ingestion.reader.MaterialPart;
import org.aiincubator.ilmai.ai.ingestion.reader.MaterialReader;
import org.aiincubator.ilmai.ai.ingestion.reader.MaterialReaderDispatcher;
import org.aiincubator.ilmai.ai.ingestion.reader.PdfRangePart;
import org.aiincubator.ilmai.ai.ingestion.reader.TextMaterialPart;
import org.aiincubator.ilmai.common.storage.BlobStorage;
import org.aiincubator.ilmai.materials.MaterialDto;
import org.aiincubator.ilmai.materials.MaterialStatus;
import org.aiincubator.ilmai.materials.MaterialsApi;
import org.aiincubator.ilmai.materials.MaterialStorageKeys;
import org.aiincubator.ilmai.materials.MaterialUploadedEvent;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@EnableConfigurationProperties(IngestionProperties.class)
public class MaterialIngestionService {

    private static final int MAX_IMAGES_PER_REQUEST = 6;

    private final MaterialsApi materialsApi;
    private final BlobStorage storage;
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final ObjectProvider<MultimodalEmbeddingApi> multimodalEmbeddingProvider;
    private final MultimodalVectorWriter multimodalVectorWriter;
    private final ApplicationEventPublisher publisher;
    private final MaterialReaderDispatcher readerDispatcher;
    private final TokenTextSplitter splitter = TokenTextSplitter.builder().build();

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMaterialUploaded(MaterialUploadedEvent event) {
        UUID materialId = event.getMaterialId();
        UUID userId = event.getUserId();
        Optional<MaterialDto> materialOpt = materialsApi.findById(materialId);
        if (materialOpt.isEmpty()) {
            log.debug("ingestion skipped — material {} no longer exists", materialId);
            return;
        }
        MaterialDto material = materialOpt.get();
        if (material.getStatus() == MaterialStatus.READY) {
            log.warn("material {} already ready; skipping ingestion", materialId);
            return;
        }
        MaterialStatus finalStatus;
        try {
            materialsApi.flushStatus(materialId, MaterialStatus.PROCESSING);

            List<MaterialPart> parts = readParts(material);
            indexParts(material, userId, parts);

            materialsApi.updateStatus(materialId, MaterialStatus.READY);
            finalStatus = MaterialStatus.READY;
        } catch (Exception ex) {
            log.warn("ingestion failed for material {}: {}", materialId, ex.toString());
            materialsApi.updateStatus(materialId, MaterialStatus.FAILED);
            finalStatus = MaterialStatus.FAILED;
        }
        publisher.publishEvent(new MaterialIngestionCompletedEvent(materialId, userId, finalStatus));
    }

    private List<MaterialPart> readParts(MaterialDto material) throws java.io.IOException {
        MaterialReader reader = readerDispatcher.dispatch(material.getContentType());
        try (InputStream in = storage.open(MaterialStorageKeys.forMaterial(material))) {
            return reader.read(in, material);
        }
    }

    private void indexParts(MaterialDto material, UUID userId, List<MaterialPart> parts) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            log.warn("VectorStore bean absent; aborting vector indexing for material {}", material.getId());
            throw new EmbeddingUnavailableException();
        }

        List<Document> textChunks = collectTextChunks(parts);
        List<PdfRangePart> pdfRanges = collect(parts, PdfRangePart.class);
        List<AudioSegmentPart> audioSegments = collect(parts, AudioSegmentPart.class);
        List<ImagePart> images = collect(parts, ImagePart.class);

        int chunkIndex = 0;
        if (!textChunks.isEmpty()) {
            for (Document chunk : textChunks) {
                stampMetadata(chunk.getMetadata(), userId, material, chunkIndex++);
                chunk.getMetadata().put("chunk_kind", "text");
            }
            vectorStore.add(textChunks);
        }

        if (!pdfRanges.isEmpty() || !audioSegments.isEmpty() || !images.isEmpty()) {
            MultimodalEmbeddingApi multimodal = multimodalEmbeddingProvider.getIfAvailable();
            if (multimodal == null || !multimodal.isAvailable()) {
                throw new EmbeddingUnavailableException();
            }
            for (PdfRangePart range : pdfRanges) {
                MultimodalContent content = new MultimodalContent(List.of(
                        new InlineDataPart("application/pdf", range.getPdfBytes())));
                float[] vector = multimodal.embed(content);
                Map<String, Object> metadata = new HashMap<>();
                stampMetadata(metadata, userId, material, chunkIndex++);
                metadata.put("chunk_kind", "pdf_range");
                metadata.put("page_start", range.getPageStart());
                metadata.put("page_end", range.getPageEnd());
                multimodalVectorWriter.write("", metadata, vector);
            }
            for (AudioSegmentPart segment : audioSegments) {
                MultimodalContent content = segmentContent(segment);
                float[] vector = multimodal.embed(content);
                Map<String, Object> metadata = new HashMap<>();
                stampMetadata(metadata, userId, material, chunkIndex++);
                metadata.put("chunk_kind", "audio_segment");
                metadata.put("audio_start_ms", segment.getStartMs());
                metadata.put("audio_end_ms", segment.getEndMs());
                multimodalVectorWriter.write("", metadata, vector);
            }
            for (int start = 0; start < images.size(); start += MAX_IMAGES_PER_REQUEST) {
                int endExclusive = Math.min(start + MAX_IMAGES_PER_REQUEST, images.size());
                List<ImagePart> batch = images.subList(start, endExclusive);
                List<MultimodalPart> mp = new ArrayList<>(batch.size());
                List<String> mimeTypes = new ArrayList<>(batch.size());
                for (ImagePart image : batch) {
                    mp.add(new InlineDataPart(image.getMimeType(), image.getData()));
                    mimeTypes.add(image.getMimeType());
                }
                float[] vector = multimodal.embed(new MultimodalContent(mp));
                Map<String, Object> metadata = new HashMap<>();
                stampMetadata(metadata, userId, material, chunkIndex++);
                metadata.put("chunk_kind", "image_set");
                metadata.put("image_count", batch.size());
                metadata.put("image_mime_types", String.join(",", mimeTypes));
                multimodalVectorWriter.write("", metadata, vector);
            }
        }
    }

    private List<Document> collectTextChunks(List<MaterialPart> parts) {
        List<Document> sources = new ArrayList<>();
        for (MaterialPart part : parts) {
            if (part instanceof TextMaterialPart text && text.getText() != null && !text.getText().isEmpty()) {
                sources.add(new Document(text.getText()));
            }
        }
        if (sources.isEmpty()) {
            return List.of();
        }
        return splitter.apply(sources);
    }

    private <T extends MaterialPart> List<T> collect(List<MaterialPart> parts, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (MaterialPart part : parts) {
            if (type.isInstance(part)) {
                out.add(type.cast(part));
            }
        }
        return out;
    }

    private MultimodalContent segmentContent(AudioSegmentPart segment) {
        return new MultimodalContent(List.of(
                new InlineDataPart(segment.getMimeType(), segment.getSegmentBytes())));
    }

    private void stampMetadata(Map<String, Object> metadata, UUID userId, MaterialDto material, int chunkIndex) {
        metadata.put("user_id", userId.toString());
        metadata.put("room_id", material.getSpaceId().toString());
        metadata.put("material_id", material.getId().toString());
        metadata.put("material_name", material.getTitle());
        metadata.put("chunk_index", chunkIndex);
    }
}
