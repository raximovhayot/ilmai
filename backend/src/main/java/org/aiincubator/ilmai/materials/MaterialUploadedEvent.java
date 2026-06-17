package org.aiincubator.ilmai.materials;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class MaterialUploadedEvent {

    private final UUID materialId;
    private final UUID userId;
}
