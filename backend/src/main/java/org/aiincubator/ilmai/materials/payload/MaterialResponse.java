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
public class MaterialResponse {

    private UUID id;
    private UUID topicId;
    private String title;
    private String contentType;
    private Long sizeBytes;
    private String status;
    private int retryCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
