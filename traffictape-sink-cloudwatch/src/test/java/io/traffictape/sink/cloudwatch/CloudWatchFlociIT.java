package io.traffictape.sink.cloudwatch;

import io.traffictape.capture.CaptureBatch;
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
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilterLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilteredLogEvent;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Writes a real batch through Floci (local AWS). Docker required;
 * skipped automatically when Docker is not running.
 */
@Testcontainers(disabledWithoutDocker = true)
class CloudWatchFlociIT {

    @Container
    static final FlociContainer floci = new FlociContainer();

    @Test
    void putLogEventsAreReadableAsCorpusLines() {
        CloudWatchLogsClient logs = CloudWatchLogsClient.builder()
                .endpointOverride(URI.create(floci.getEndpoint()))
                .region(Region.of(floci.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(floci.getAccessKey(), floci.getSecretKey())))
                .build();

        CloudWatchCaptureSink sink = new CloudWatchCaptureSink(logs, "/traffictape/it", "task-a");
        HttpTransaction tx = new HttpTransaction(
                "1", EventType.HTTP_TRANSACTION, Direction.INBOUND, Instant.now(),
                null, null, "POST", "/orders", "/orders", Map.of(), null, "{sku:string}", "201",
                12, null, null);
        sink.write(new CaptureBatch(List.of(tx), new StatisticsRegistry(32).snapshot()));

        List<String> messages = logs.filterLogEvents(FilterLogEventsRequest.builder()
                        .logGroupName("/traffictape/it")
                        .logStreamNames("task-a")
                        .build())
                .events()
                .stream()
                .map(FilteredLogEvent::message)
                .toList();

        assertThat(messages).anyMatch(m -> m.contains("\"eventType\":\"HTTP_TRANSACTION\"") && m.contains("POST"));
        assertThat(messages).anyMatch(m -> m.contains("\"eventType\":\"STATISTICS\"") && m.contains("observedRequests"));
    }
}
