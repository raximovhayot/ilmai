package org.aiincubator.ilmai.ai;

import java.util.List;
import java.util.UUID;

public interface RetrievalApi {

    default List<RetrievedChunkDto> retrieve(UUID userId, String query) {
        return retrieve(userId, null, query);
    }

    List<RetrievedChunkDto> retrieve(UUID userId, UUID roomId, String query);
}
