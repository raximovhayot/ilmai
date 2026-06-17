package org.aiincubator.ilmai.common.storage;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StoredBlob {

    private final String storageKey;
    private final String contentType;
    private final long sizeBytes;
}
