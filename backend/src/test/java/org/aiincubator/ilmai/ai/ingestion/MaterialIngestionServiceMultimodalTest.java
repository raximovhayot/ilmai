package org.aiincubator.ilmai.ai.ingestion;

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
import org.aiincubator.ilmai.common.storage.BlobStorage;
import org.aiincubator.ilmai.materials.MaterialDto;
import org.aiincubator.ilmai.materials.MaterialStatus;
import org.aiincubator.ilmai.materials.MaterialStorageKeys;
import org.aiincubator.ilmai.materials.MaterialUploadedEvent;
import org.aiincubator.ilmai.materials.MaterialsApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialIngestionServiceMultimodalTest {

    @Mock MaterialsApi materialsApi;
    @Mock BlobStorage storage;
    @Mock ObjectProvider<VectorStore> vectorStoreProvider;
    @Mock ObjectProvider<MultimodalEmbeddingApi> multimodalEmbeddingProvider;
    @Mock MultimodalVectorWriter multimodalVectorWriter;
    @Mock ApplicationEventPublisher publisher;

    private final Map<UUID, MaterialDto> store = new HashMap<>();
    private MultimodalEmbeddingApi multimodal;

    @BeforeEach
    void setUp() {
        lenient().doAnswer(inv -> {
            UUID id = inv.getArgument(0);
            MaterialStatus s = inv.getArgument(1);
            MaterialDto cur = store.get(id);
            if (cur != null) store.put(id, withStatus(cur, s));
            return null;
        }).when(materialsApi).flushStatus(any(UUID.class), any(MaterialStatus.class));
        lenient().doAnswer(inv -> {
            UUID id = inv.getArgument(0);
            MaterialStatus s = inv.getArgument(1);
            MaterialDto cur = store.get(id);
            if (cur != null) store.put(id, withStatus(cur, s));
            return null;
        }).when(materialsApi).updateStatus(any(UUID.class), any(MaterialStatus.class));

        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStoreProvider.getIfAvailable()).thenReturn(vectorStore);
        multimodal = mock(MultimodalEmbeddingApi.class);
        when(multimodal.isAvailable()).thenReturn(true);
        when(multimodal.embed(any())).thenReturn(new float[]{0.1f, 0.2f});
        when(multimodalEmbeddingProvider.getIfAvailable()).thenReturn(multimodal);
    }

    private MaterialDto withStatus(MaterialDto m, MaterialStatus s) {
        return new MaterialDto(m.getId(), m.getTopicId(), m.getSpaceId(), m.getTitle(),
                m.getContentType(), m.getSizeBytes(), s, m.getRetryCount(), m.getCreatedAt(), m.getUpdatedAt());
    }

    private MaterialDto seedMaterial(String contentType) {
        UUID id = UUID.randomUUID();
        MaterialDto m = new MaterialDto(id, UUID.randomUUID(), UUID.randomUUID(),
                "fixture", contentType, 11L, MaterialStatus.PENDING, 0, null, null);
        store.put(id, m);
        when(materialsApi.findById(eq(id))).thenAnswer(inv -> Optional.ofNullable(store.get(id)));
        return m;
    }

    private MaterialIngestionService serviceFor(String contentType, List<MaterialPart> parts) {
        MaterialReader stubReader = new MaterialReader() {
            @Override
            public boolean supports(String ct) {
                return contentType.equals(ct);
            }
            @Override
            public List<MaterialPart> read(java.io.InputStream blob, MaterialDto m) {
                return parts;
            }
        };
        MaterialReaderDispatcher dispatcher = new MaterialReaderDispatcher(List.of(stubReader));
        return new MaterialIngestionService(
                materialsApi, storage, vectorStoreProvider, multimodalEmbeddingProvider,
                multimodalVectorWriter, publisher, dispatcher);
    }

    @Test
    void pdfRange_routesToPdfBranch_withApplicationPdfPart() throws IOException {
        UUID userId = UUID.randomUUID();
        MaterialDto material = seedMaterial("application/pdf");
        when(storage.open(MaterialStorageKeys.forMaterial(material)))
                .thenReturn(new ByteArrayInputStream(new byte[]{9}));
        MaterialIngestionService ingestion = serviceFor("application/pdf",
                List.of(new PdfRangePart(1, 6, new byte[]{1, 2, 3})));

        ingestion.onMaterialUploaded(new MaterialUploadedEvent(material.getId(), userId));

        assertThat(store.get(material.getId()).getStatus()).isEqualTo(MaterialStatus.READY);
        MultimodalContent content = captureEmbeddedContent();
        assertThat(content.getParts()).hasSize(1);
        InlineDataPart part = (InlineDataPart) content.getParts().get(0);
        assertThat(part.getMimeType()).isEqualTo("application/pdf");
        assertThat(part.getData()).containsExactly(1, 2, 3);

        Map<String, Object> metadata = captureWrittenMetadata();
        assertThat(metadata)
                .containsEntry("chunk_kind", "pdf_range")
                .containsEntry("page_start", 1)
                .containsEntry("page_end", 6);
    }

    @Test
    void audioSegment_routesToAudioBranch_withMimePart() throws IOException {
        UUID userId = UUID.randomUUID();
        MaterialDto material = seedMaterial("audio/mpeg");
        when(storage.open(MaterialStorageKeys.forMaterial(material)))
                .thenReturn(new ByteArrayInputStream(new byte[]{9}));
        MaterialIngestionService ingestion = serviceFor("audio/mpeg",
                List.of(new AudioSegmentPart(0L, 120000L, "audio/mpeg", new byte[]{4, 5})));

        ingestion.onMaterialUploaded(new MaterialUploadedEvent(material.getId(), userId));

        assertThat(store.get(material.getId()).getStatus()).isEqualTo(MaterialStatus.READY);
        MultimodalContent content = captureEmbeddedContent();
        assertThat(content.getParts()).hasSize(1);
        InlineDataPart part = (InlineDataPart) content.getParts().get(0);
        assertThat(part.getMimeType()).isEqualTo("audio/mpeg");

        Map<String, Object> metadata = captureWrittenMetadata();
        assertThat(metadata)
                .containsEntry("chunk_kind", "audio_segment")
                .containsEntry("audio_start_ms", 0L)
                .containsEntry("audio_end_ms", 120000L);
    }

    @Test
    void images_routeToImageBranch_batchedUpToSixPerEmbed() throws IOException {
        UUID userId = UUID.randomUUID();
        MaterialDto material = seedMaterial("image/png");
        when(storage.open(MaterialStorageKeys.forMaterial(material)))
                .thenReturn(new ByteArrayInputStream(new byte[]{9}));
        List<MaterialPart> parts = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) {
            parts.add(new ImagePart("image/png", new byte[]{(byte) i}));
        }
        MaterialIngestionService ingestion = serviceFor("image/png", parts);

        ingestion.onMaterialUploaded(new MaterialUploadedEvent(material.getId(), userId));

        assertThat(store.get(material.getId()).getStatus()).isEqualTo(MaterialStatus.READY);
        ArgumentCaptor<MultimodalContent> contentCaptor = ArgumentCaptor.forClass(MultimodalContent.class);
        verify(multimodal, times(2)).embed(contentCaptor.capture());
        List<MultimodalContent> contents = contentCaptor.getAllValues();
        assertThat(contents.get(0).getParts()).hasSize(6);
        assertThat(contents.get(1).getParts()).hasSize(1);
        for (MultimodalPart p : contents.get(0).getParts()) {
            assertThat(((InlineDataPart) p).getMimeType()).isEqualTo("image/png");
        }

        ArgumentCaptor<Map<String, Object>> metadataCaptor = captureMetadataCaptor();
        verify(multimodalVectorWriter, times(2)).write(anyString(), metadataCaptor.capture(), any(float[].class));
        Map<String, Object> first = metadataCaptor.getAllValues().get(0);
        assertThat(first)
                .containsEntry("chunk_kind", "image_set")
                .containsEntry("image_count", 6)
                .containsEntry("image_mime_types", "image/png,image/png,image/png,image/png,image/png,image/png");
        assertThat(metadataCaptor.getAllValues().get(1)).containsEntry("image_count", 1);
    }

    private MultimodalContent captureEmbeddedContent() {
        ArgumentCaptor<MultimodalContent> captor = ArgumentCaptor.forClass(MultimodalContent.class);
        verify(multimodal).embed(captor.capture());
        return captor.getValue();
    }

    private Map<String, Object> captureWrittenMetadata() {
        ArgumentCaptor<Map<String, Object>> captor = captureMetadataCaptor();
        verify(multimodalVectorWriter).write(anyString(), captor.capture(), any(float[].class));
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, Object>> captureMetadataCaptor() {
        return ArgumentCaptor.forClass(Map.class);
    }
}
