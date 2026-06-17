package org.aiincubator.ilmai.materials;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public final class TopicDto {

    private final UUID id;
    private final UUID spaceId;
    private final String name;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;
}
