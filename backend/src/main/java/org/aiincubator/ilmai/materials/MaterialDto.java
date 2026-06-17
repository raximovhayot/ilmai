package org.aiincubator.ilmai.materials;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public final class MaterialDto {

    private final UUID id;
    private final UUID topicId;
    private final UUID spaceId;
    private final String title;
    private final String contentType;
    private final Long sizeBytes;
    private final MaterialStatus status;
    private final int retryCount;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
}
