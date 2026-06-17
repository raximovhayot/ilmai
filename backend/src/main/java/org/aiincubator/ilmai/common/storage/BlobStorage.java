package org.aiincubator.ilmai.common.storage;

import java.io.IOException;
import java.io.InputStream;

public interface BlobStorage {

    StoredBlob put(String storageKey, InputStream content, long size, String contentType) throws IOException;

    InputStream open(String storageKey) throws IOException;

    void delete(String storageKey) throws IOException;
}
