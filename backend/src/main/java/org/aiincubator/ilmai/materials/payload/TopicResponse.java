package org.aiincubator.ilmai.materials.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicResponse {

    private UUID id;
    private UUID spaceId;
    private String name;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
