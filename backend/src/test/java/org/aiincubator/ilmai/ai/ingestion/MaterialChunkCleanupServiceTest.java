package org.aiincubator.ilmai.ai.ingestion;

import org.aiincubator.ilmai.materials.MaterialDeletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.ObjectProvider;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialChunkCleanupServiceTest {

    @Mock ObjectProvider<VectorStore> vectorStoreProvider;
    @Mock VectorStore vectorStore;

    @Test
    void onMaterialDeleted_deletesByMaterialIdFilter() {
        when(vectorStoreProvider.getIfAvailable()).thenReturn(vectorStore);
        MaterialChunkCleanupService service = new MaterialChunkCleanupService(vectorStoreProvider);
        UUID materialId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        service.onMaterialDeleted(new MaterialDeletedEvent(materialId, userId));

        ArgumentCaptor<Filter.Expression> captor = ArgumentCaptor.forClass(Filter.Expression.class);
        verify(vectorStore).delete(captor.capture());
        Filter.Expression filter = captor.getValue();
        assertThat(filter).isNotNull();
        assertThat(filter.toString()).contains("material_id").contains(materialId.toString());
    }

    @Test
    void onMaterialDeleted_isNoOpWhenVectorStoreAbsent() {
        when(vectorStoreProvider.getIfAvailable()).thenReturn(null);
        MaterialChunkCleanupService service = new MaterialChunkCleanupService(vectorStoreProvider);

        service.onMaterialDeleted(new MaterialDeletedEvent(UUID.randomUUID(), UUID.randomUUID()));

        verify(vectorStore, never()).delete(any(Filter.Expression.class));
    }

    @Test
    void onMaterialDeleted_swallowsVectorStoreDeleteExceptions() {
        when(vectorStoreProvider.getIfAvailable()).thenReturn(vectorStore);
        doThrow(new RuntimeException("pgvector unreachable")).when(vectorStore).delete(any(Filter.Expression.class));
        MaterialChunkCleanupService service = new MaterialChunkCleanupService(vectorStoreProvider);

        assertThatCode(() -> service.onMaterialDeleted(
                new MaterialDeletedEvent(UUID.randomUUID(), UUID.randomUUID())))
                .doesNotThrowAnyException();
    }
}
