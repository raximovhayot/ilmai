package org.aiincubator.ilmai.common.storage;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;

@RequiredArgsConstructor
public class S3BlobStorage implements BlobStorage {

    private final S3Client s3Client;
    private final S3StorageProperties properties;

    @Override
    public StoredBlob put(String storageKey, InputStream content, long size, String contentType) throws IOException {
        try {
            PutObjectRequest.Builder request = PutObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(storageKey);
            if (contentType != null && !contentType.isBlank()) {
                request.contentType(contentType);
            }
            byte[] bytes = content.readAllBytes();
            request.contentLength((long) bytes.length);
            s3Client.putObject(request.build(), RequestBody.fromBytes(bytes));
            return new StoredBlob(storageKey, contentType, bytes.length);
        } catch (RuntimeException ex) {
            throw new IOException("failed to put object to S3 bucket " + properties.getBucket(), ex);
        }
    }

    @Override
    public InputStream open(String storageKey) throws IOException {
        try {
            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(storageKey)
                    .build());
            return response;
        } catch (RuntimeException ex) {
            throw new IOException("failed to open object from S3 bucket " + properties.getBucket(), ex);
        }
    }

    @Override
    public void delete(String storageKey) throws IOException {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(storageKey)
                    .build());
        } catch (RuntimeException ex) {
            throw new IOException("failed to delete object from S3 bucket " + properties.getBucket(), ex);
        }
    }
}
