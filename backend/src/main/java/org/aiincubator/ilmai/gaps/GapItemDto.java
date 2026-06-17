package org.aiincubator.ilmai.gaps;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public final class GapItemDto {

    private final UUID id;
    private final String concept;
    private final int missCount;
    private final int hitCount;
    private final double accuracy;
    private final OffsetDateTime lastSeenAt;
    private final UUID suggestedMaterialId;
    private final String suggestedMaterialName;
    private final GapTrend trend;
}
