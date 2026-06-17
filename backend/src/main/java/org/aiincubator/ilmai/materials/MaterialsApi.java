package org.aiincubator.ilmai.materials;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaterialsApi {

    boolean hasReadyMaterialsForUser(UUID userId);

    List<MaterialDto> findReadyForUser(UUID userId);

    Optional<MaterialDto> findOwnedByUser(UUID materialId, UUID userId);

    Optional<MaterialDto> findById(UUID materialId);

    void updateStatus(UUID materialId, MaterialStatus status);

    void flushStatus(UUID materialId, MaterialStatus status);

    MaterialDto ingestUpload(UUID userId, String filename, String contentType, byte[] content);

    Optional<TopicDto> findTopicOwnedByUser(UUID topicId, UUID userId);

    List<TopicDto> findAllTopicsByUser(UUID userId);
}
