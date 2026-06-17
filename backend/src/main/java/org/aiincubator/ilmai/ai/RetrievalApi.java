package org.aiincubator.ilmai.ai;

import java.util.List;
import java.util.UUID;

public interface RetrievalApi {

    List<RetrievedChunkDto> retrieve(UUID userId, String query);
}
