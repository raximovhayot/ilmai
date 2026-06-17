package org.aiincubator.ilmai.ai.ingestion.support;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class UserMaterialFixture {

    private final UUID userId;
    private final UUID materialId;
}
