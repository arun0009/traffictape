package io.traffictape.sink.s3;

import io.traffictape.capture.CaptureBatch;
import io.traffictape.capture.ObjectStoreCaptureSink;
import io.traffictape.model.Direction;
import io.traffictape.model.EventType;
import io.traffictape.model.HttpTransaction;
import io.traffictape.statistics.StatisticsRegistry;
import io.floci.testcontainers.FlociContainer;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Writes a real corpus through Floci S3. Docker required;
 * skipped automatically when Docker is not running.
 */
@Testcontainers(disabledWithoutDocker = true)
class S3FlociIT {

    @Container
    static final FlociContainer floci = new FlociContainer();

    @Test
    void writesMetadataStatisticsAndGzipEvents() throws Exception {
        S3Client s3 = S3Client.builder()
                .endpointOverride(URI.create(floci.getEndpoint()))
                .region(Region.of(floci.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(floci.getAccessKey(), floci.getSecretKey())))
                .forcePathStyle(true)
                .build();
        s3.createBucket(CreateBucketRequest.builder().bucket("qa-tape").build());

        ObjectStoreCaptureSink sink = new ObjectStoreCaptureSink(
                new S3ObjectPutter(s3, "qa-tape", "payments-api/task-a"),
                Map.of("serviceName", "payments-api", "output", "s3://qa-tape/payments-api/task-a"));
        HttpTransaction tx = new HttpTransaction(
                "1", EventType.HTTP_TRANSACTION, Direction.INBOUND, Instant.now(),
                null, null, "GET", "/widgets/{id}", "/widgets/1", Map.of(), null, "none", "200",
                4, null, null);
        sink.write(new CaptureBatch(List.of(tx), new StatisticsRegistry(32).snapshot()));
        sink.close();

        var listed = s3.listObjectsV2(ListObjectsV2Request.builder().bucket("qa-tape").prefix("payments-api/task-a/").build());
        assertThat(listed.contents()).extracting(o -> o.key())
                .contains(
                        "payments-api/task-a/metadata.json",
                        "payments-api/task-a/statistics.json",
                        "payments-api/task-a/gaps.json",
                        "payments-api/task-a/fanout.json",
                        "payments-api/task-a/FOR_CLAUDE.md",
                        "payments-api/task-a/events/events-000001.jsonl.gz");

        String metadata = getUtf8(s3, "payments-api/task-a/metadata.json");
        assertThat(metadata).contains("traffictape").contains("payments-api");
        String statistics = getUtf8(s3, "payments-api/task-a/statistics.json");
        assertThat(statistics).contains("observedRequests");
        byte[] gz = getBytes(s3, "payments-api/task-a/events/events-000001.jsonl.gz");
        String jsonl;
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
            jsonl = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(jsonl).contains("\"GET\"").contains("/widgets/{id}");
    }

    private static String getUtf8(S3Client s3, String key) {
        return new String(getBytes(s3, key), StandardCharsets.UTF_8);
    }

    private static byte[] getBytes(S3Client s3, String key) {
        ResponseBytes<GetObjectResponse> body = s3.getObjectAsBytes(GetObjectRequest.builder()
                .bucket("qa-tape")
                .key(key)
                .build());
        return body.asByteArray();
    }
}
