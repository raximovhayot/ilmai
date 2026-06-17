package org.aiincubator.ilmai.ai.ingestion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.materials.MaterialDeletedEvent;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaterialChunkCleanupService {

    private final ObjectProvider<VectorStore> vectorStoreProvider;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMaterialDeleted(MaterialDeletedEvent event) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            log.debug("VectorStore bean absent; skipping chunk cleanup for material {}", event.getMaterialId());
            return;
        }
        Filter.Expression filter = new FilterExpressionBuilder()
                .eq("material_id", event.getMaterialId().toString())
                .build();
        try {
            vectorStore.delete(filter);
        } catch (RuntimeException ex) {
            log.warn("vector chunk cleanup failed for material {}: {}", event.getMaterialId(), ex.toString());
        }
    }
}
