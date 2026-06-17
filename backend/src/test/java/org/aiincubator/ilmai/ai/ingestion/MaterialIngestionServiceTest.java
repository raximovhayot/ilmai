package org.aiincubator.ilmai.ai.ingestion;

import org.aiincubator.ilmai.ai.MaterialIngestionCompletedEvent;
import org.aiincubator.ilmai.ai.MultimodalEmbeddingApi;
import org.aiincubator.ilmai.ai.ingestion.reader.MaterialReaderDispatcher;
import org.aiincubator.ilmai.ai.ingestion.reader.PlainTextReader;
import org.aiincubator.ilmai.common.storage.BlobStorage;
import org.aiincubator.ilmai.materials.MaterialDto;
import org.aiincubator.ilmai.materials.MaterialStatus;
import org.aiincubator.ilmai.materials.MaterialsApi;
import org.aiincubator.ilmai.materials.MaterialStorageKeys;
import org.aiincubator.ilmai.materials.MaterialUploadedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialIngestionServiceTest {

    @Mock MaterialsApi materialsApi;
    @Mock BlobStorage storage;
    @Mock ObjectProvider<VectorStore> vectorStoreProvider;
    @Mock ObjectProvider<MultimodalEmbeddingApi> multimodalEmbeddingProvider;
    @Mock MultimodalVectorWriter multimodalVectorWriter;
    @Mock ApplicationEventPublisher publisher;

    private MaterialIngestionService ingestion;
    private MaterialReaderDispatcher dispatcher;
    private final Map<UUID, MaterialDto> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        dispatcher = new MaterialReaderDispatcher(List.of(new PlainTextReader()));
        ingestion = new MaterialIngestionService(
                materialsApi, storage, vectorStoreProvider, multimodalEmbeddingProvider,
                multimodalVectorWriter, publisher, dispatcher);
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
    }

    private MaterialDto withStatus(MaterialDto m, MaterialStatus s) {
        return new MaterialDto(m.getId(), m.getTopicId(), m.getSpaceId(), m.getTitle(),
                m.getContentType(), m.getSizeBytes(), s, m.getRetryCount(), m.getCreatedAt(), m.getUpdatedAt());
    }

    private MaterialDto seedMaterial(String contentType) {
        UUID id = UUID.randomUUID();
        MaterialDto m = new MaterialDto(id, UUID.randomUUID(), UUID.randomUUID(),
                "hello", contentType, 11L, MaterialStatus.PENDING, 0, null, null);
        store.put(id, m);
        when(materialsApi.findById(eq(id))).thenAnswer(inv -> Optional.ofNullable(store.get(id)));
        return m;
    }

    @Test
    void onMaterialUploaded_marksFailedWhenVectorStoreAbsent() throws IOException {
        UUID userId = UUID.randomUUID();
        MaterialDto material = seedMaterial("text/plain; charset=utf-8");
        when(storage.open(MaterialStorageKeys.forMaterial(material)))
                .thenReturn(new ByteArrayInputStream("hello world".getBytes(StandardCharsets.UTF_8)));
        when(vectorStoreProvider.getIfAvailable()).thenReturn(null);

        ingestion.onMaterialUploaded(new MaterialUploadedEvent(material.getId(), userId));

        assertThat(store.get(material.getId()).getStatus()).isEqualTo(MaterialStatus.FAILED);
        MaterialIngestionCompletedEvent published = capturePublishedEvent();
        assertThat(published.getMaterialId()).isEqualTo(material.getId());
        assertThat(published.getUserId()).isEqualTo(userId);
        assertThat(published.getStatus()).isEqualTo(MaterialStatus.FAILED);
    }

    @Test
    void onMaterialUploaded_marksFailedWhenContentTypeUnknown() {
        UUID userId = UUID.randomUUID();
        MaterialDto material = seedMaterial("image/png");

        ingestion.onMaterialUploaded(new MaterialUploadedEvent(material.getId(), userId));

        assertThat(store.get(material.getId()).getStatus()).isEqualTo(MaterialStatus.FAILED);
        MaterialIngestionCompletedEvent published = capturePublishedEvent();
        assertThat(published.getStatus()).isEqualTo(MaterialStatus.FAILED);
    }

    @Test
    void onMaterialUploaded_marksReadyAndCallsVectorStoreWhenAvailable() throws IOException {
        UUID userId = UUID.randomUUID();
        MaterialDto material = seedMaterial("text/plain; charset=utf-8");
        when(storage.open(MaterialStorageKeys.forMaterial(material)))
                .thenReturn(new ByteArrayInputStream("hello world".getBytes(StandardCharsets.UTF_8)));
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStoreProvider.getIfAvailable()).thenReturn(vectorStore);

        ingestion.onMaterialUploaded(new MaterialUploadedEvent(material.getId(), userId));

        assertThat(store.get(material.getId()).getStatus()).isEqualTo(MaterialStatus.READY);
        verify(vectorStore).add(any(List.class));
        MaterialIngestionCompletedEvent published = capturePublishedEvent();
        assertThat(published.getStatus()).isEqualTo(MaterialStatus.READY);
    }

    @Test
    void onMaterialUploaded_marksFailedOnStorageException() throws IOException {
        UUID userId = UUID.randomUUID();
        MaterialDto material = seedMaterial("text/plain; charset=utf-8");
        when(storage.open(anyString())).thenThrow(new IOException("S3 unreachable"));

        ingestion.onMaterialUploaded(new MaterialUploadedEvent(material.getId(), userId));

        assertThat(store.get(material.getId()).getStatus()).isEqualTo(MaterialStatus.FAILED);
        MaterialIngestionCompletedEvent published = capturePublishedEvent();
        assertThat(published.getStatus()).isEqualTo(MaterialStatus.FAILED);
    }

    @Test
    void onMaterialUploaded_noOpWhenMaterialMissing() throws IOException {
        UUID userId = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        when(materialsApi.findById(missing)).thenReturn(Optional.empty());

        ingestion.onMaterialUploaded(new MaterialUploadedEvent(missing, userId));

        verify(storage, never()).open(anyString());
        verify(vectorStoreProvider, never()).getIfAvailable();
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void indexing_attachesPerUserMetadataOnChunks() throws IOException {
        UUID userId = UUID.randomUUID();
        MaterialDto material = seedMaterial("text/plain; charset=utf-8");
        when(storage.open(MaterialStorageKeys.forMaterial(material)))
                .thenReturn(new ByteArrayInputStream("hello world".getBytes(StandardCharsets.UTF_8)));

        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStoreProvider.getIfAvailable()).thenReturn(vectorStore);

        ingestion.onMaterialUploaded(new MaterialUploadedEvent(material.getId(), userId));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        List<Document> added = captor.getValue();
        assertThat(added).isNotEmpty();
        for (Document chunk : added) {
            assertThat(chunk.getMetadata())
                    .containsEntry("user_id", userId.toString())
                    .containsEntry("material_id", material.getId().toString())
                    .containsEntry("chunk_kind", "text");
            assertThat(chunk.getMetadata().get("chunk_index")).isInstanceOf(Integer.class);
        }
    }

    @Test
    void onMaterialUploaded_pdfRange_writesMultimodalVectorWithChunkKind() throws IOException {
        UUID userId = UUID.randomUUID();
        MaterialDto material = seedMaterial("application/pdf");

        org.aiincubator.ilmai.ai.ingestion.reader.MaterialReader stubReader = new org.aiincubator.ilmai.ai.ingestion.reader.MaterialReader() {
            @Override
            public boolean supports(String contentType) {
                return "application/pdf".equals(contentType);
            }
            @Override
            public List<org.aiincubator.ilmai.ai.ingestion.reader.MaterialPart> read(java.io.InputStream blob, MaterialDto m) {
                return List.of(new org.aiincubator.ilmai.ai.ingestion.reader.PdfRangePart(
                        1, 6, new byte[]{1, 2, 3}));
            }
        };
        dispatcher = new MaterialReaderDispatcher(List.of(stubReader));
        ingestion = new MaterialIngestionService(
                materialsApi, storage, vectorStoreProvider, multimodalEmbeddingProvider,
                multimodalVectorWriter, publisher, dispatcher);
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

        when(storage.open(MaterialStorageKeys.forMaterial(material)))
                .thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStoreProvider.getIfAvailable()).thenReturn(vectorStore);
        MultimodalEmbeddingApi multimodal = mock(MultimodalEmbeddingApi.class);
        when(multimodal.isAvailable()).thenReturn(true);
        when(multimodal.embed(any())).thenReturn(new float[]{0.1f, 0.2f});
        when(multimodalEmbeddingProvider.getIfAvailable()).thenReturn(multimodal);

        ingestion.onMaterialUploaded(new MaterialUploadedEvent(material.getId(), userId));

        assertThat(store.get(material.getId()).getStatus()).isEqualTo(MaterialStatus.READY);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(java.util.Map.class);
        verify(multimodalVectorWriter).write(anyString(), metadataCaptor.capture(), any(float[].class));
        java.util.Map<String, Object> metadata = metadataCaptor.getValue();
        assertThat(metadata)
                .containsEntry("user_id", userId.toString())
                .containsEntry("material_id", material.getId().toString())
                .containsEntry("chunk_kind", "pdf_range")
                .containsEntry("page_start", 1)
                .containsEntry("page_end", 6);
    }

    private MaterialIngestionCompletedEvent capturePublishedEvent() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(captor.capture());
        Object event = captor.getValue();
        assertThat(event).isInstanceOf(MaterialIngestionCompletedEvent.class);
        return (MaterialIngestionCompletedEvent) event;
    }
}
