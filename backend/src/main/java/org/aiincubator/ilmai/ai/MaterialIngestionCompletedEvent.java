package org.aiincubator.ilmai.ai;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.aiincubator.ilmai.materials.MaterialStatus;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class MaterialIngestionCompletedEvent {
    private final UUID materialId;
    private final UUID userId;
    private final MaterialStatus status;
}
