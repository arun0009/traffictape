package io.traffictape.sink.s3;

import io.traffictape.capture.ObjectStoreCaptureSink;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.Objects;

/**
 * Puts corpus objects under {@code s3://bucket/{prefix}/…}. Prefix is unique per task by default.
 */
public final class S3ObjectPutter implements ObjectStoreCaptureSink.ObjectPutter {

    private final S3Client client;
    private final String bucket;
    private final String keyPrefix;

    public S3ObjectPutter(S3Client client, String bucket, String keyPrefix) {
        this.client = Objects.requireNonNull(client, "client");
        this.bucket = Objects.requireNonNull(bucket, "bucket");
        this.keyPrefix = InstancePrefix.trimSlashes(keyPrefix);
    }

    @Override
    public void put(String relativePath, byte[] content, String contentType) {
        String key = join(keyPrefix, relativePath);
        PutObjectRequest.Builder request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key);
        if (contentType != null && !contentType.isBlank()) {
            request.contentType(contentType);
        }
        client.putObject(request.build(), RequestBody.fromBytes(content == null ? new byte[0] : content));
    }

    public String bucket() {
        return bucket;
    }

    public String keyPrefix() {
        return keyPrefix;
    }

    static String join(String prefix, String relativePath) {
        String path = InstancePrefix.trimSlashes(relativePath);
        if (prefix == null || prefix.isBlank()) {
            return path;
        }
        if (path.isBlank()) {
            return prefix;
        }
        return prefix + "/" + path;
    }
}
