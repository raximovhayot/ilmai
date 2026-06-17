package org.aiincubator.ilmai.ai.ingestion.support;

import org.aiincubator.ilmai.common.storage.BlobStorage;
import org.aiincubator.ilmai.common.storage.StoredBlob;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryBlobStorage implements BlobStorage {

    private final Map<String, byte[]> blobs = new ConcurrentHashMap<>();
    private final Map<String, String> contentTypes = new ConcurrentHashMap<>();

    @Override
    public StoredBlob put(String storageKey, InputStream content, long size, String contentType) throws IOException {
        byte[] bytes = content.readAllBytes();
        blobs.put(storageKey, bytes);
        if (contentType != null) {
            contentTypes.put(storageKey, contentType);
        }
        return new StoredBlob(storageKey, contentType, bytes.length);
    }

    @Override
    public InputStream open(String storageKey) throws IOException {
        byte[] bytes = blobs.get(storageKey);
        if (bytes == null) {
            throw new IOException("blob not found: " + storageKey);
        }
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public void delete(String storageKey) {
        blobs.remove(storageKey);
        contentTypes.remove(storageKey);
    }

    public int size() {
        return blobs.size();
    }
}
