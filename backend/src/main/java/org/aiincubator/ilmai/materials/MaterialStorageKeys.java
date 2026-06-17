package org.aiincubator.ilmai.materials;

import java.util.UUID;

public final class MaterialStorageKeys {

    private MaterialStorageKeys() {
    }

    public static String forMaterial(MaterialDto material) {
        return forCoordinates(material.getSpaceId(), material.getId());
    }

    public static String forCoordinates(UUID spaceId, UUID materialId) {
        return spaceId + "/" + materialId;
    }
}
