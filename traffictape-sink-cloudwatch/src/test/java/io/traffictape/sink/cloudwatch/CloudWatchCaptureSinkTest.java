package io.traffictape.sink.cloudwatch;

import io.traffictape.capture.CaptureBatch;
import io.traffictape.model.Direction;
import io.traffictape.model.EventType;
import io.traffictape.model.HttpTransaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogGroupRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogStreamRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutLogEventsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.ResourceAlreadyExistsException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudWatchCaptureSinkTest {

    @Mock
    CloudWatchLogsClient client;

    @Test
    void putsOneJsonEventPerTransaction() {
        when(client.putLogEvents(any(PutLogEventsRequest.class)))
                .thenReturn(PutLogEventsResponse.builder().build());
        CloudWatchCaptureSink sink = new CloudWatchCaptureSink(client, "/traffictape/qa/payments", "task-a");

        sink.write(new CaptureBatch(List.of(tx("GET"), tx("POST")), null));

        ArgumentCaptor<PutLogEventsRequest> captor = ArgumentCaptor.forClass(PutLogEventsRequest.class);
        verify(client).putLogEvents(captor.capture());
        PutLogEventsRequest req = captor.getValue();
        assertThat(req.logGroupName()).isEqualTo("/traffictape/qa/payments");
        assertThat(req.logStreamName()).isEqualTo("task-a");
        assertThat(req.logEvents()).hasSize(2);
        assertThat(req.logEvents().get(0).message()).contains("\"method\":\"GET\"");
        assertThat(req.logEvents().get(1).message()).contains("\"method\":\"POST\"");
        verify(client).createLogGroup(any(CreateLogGroupRequest.class));
        verify(client).createLogStream(any(CreateLogStreamRequest.class));
    }

    @Test
    void alreadyExistingGroupAndStreamAreFine() {
        when(client.createLogGroup(any(CreateLogGroupRequest.class)))
                .thenThrow(ResourceAlreadyExistsException.builder().message("group").build());
        when(client.createLogStream(any(CreateLogStreamRequest.class)))
                .thenThrow(ResourceAlreadyExistsException.builder().message("stream").build());
        when(client.putLogEvents(any(PutLogEventsRequest.class)))
                .thenReturn(PutLogEventsResponse.builder().build());

        new CloudWatchCaptureSink(client, "/traffictape/qa/payments", "task-a")
                .write(new CaptureBatch(List.of(tx("GET")), null));

        verify(client).putLogEvents(any(PutLogEventsRequest.class));
    }

    @Test
    void putFailurePropagatesForWorkerToDropBatch() {
        when(client.putLogEvents(any(PutLogEventsRequest.class)))
                .thenThrow(new RuntimeException("throttled"));
        CloudWatchCaptureSink sink = new CloudWatchCaptureSink(client, "/traffictape/qa/payments", "task-a");

        assertThatThrownBy(() -> sink.write(new CaptureBatch(List.of(tx("GET")), null)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("throttled");
    }

    @Test
    void writesStatisticsEventWhenSnapshotPresent() {
        when(client.putLogEvents(any(PutLogEventsRequest.class)))
                .thenReturn(PutLogEventsResponse.builder().build());
        CloudWatchCaptureSink sink = new CloudWatchCaptureSink(client, "/traffictape/qa/payments", "task-a");
        var stats = new io.traffictape.statistics.StatisticsRegistry(10).snapshot();

        sink.write(new CaptureBatch(List.of(tx("GET")), stats));

        ArgumentCaptor<PutLogEventsRequest> captor = ArgumentCaptor.forClass(PutLogEventsRequest.class);
        verify(client).putLogEvents(captor.capture());
        List<String> messages = captor.getValue().logEvents().stream()
                .map(e -> e.message())
                .toList();
        assertThat(messages).anyMatch(m -> m.contains("\"eventType\":\"HTTP_TRANSACTION\""));
        assertThat(messages).anyMatch(m -> m.contains("\"eventType\":\"STATISTICS\""));
        assertThat(messages).anyMatch(m -> m.contains("\"observedRequests\""));
        assertThat(messages).anyMatch(m -> m.contains("\"captureReady\""));
    }

    @Test
    void emptyBatchDoesNotCallCloudWatch() {
        new CloudWatchCaptureSink(client, "/traffictape/qa/payments", "task-a")
                .write(new CaptureBatch(List.of(), null));
        verify(client, never()).putLogEvents(any(PutLogEventsRequest.class));
        verify(client, never()).createLogGroup(any(CreateLogGroupRequest.class));
    }

    @Test
    void twoWritesReuseTheSameStream() {
        when(client.putLogEvents(any(PutLogEventsRequest.class)))
                .thenReturn(PutLogEventsResponse.builder().build());
        CloudWatchCaptureSink sink = new CloudWatchCaptureSink(client, "/traffictape/qa/payments", "task-a");
        sink.write(new CaptureBatch(List.of(tx("GET")), null));
        sink.write(new CaptureBatch(List.of(tx("POST")), null));
        verify(client, times(1)).createLogGroup(any(CreateLogGroupRequest.class));
        verify(client, times(1)).createLogStream(any(CreateLogStreamRequest.class));
        verify(client, times(2)).putLogEvents(any(PutLogEventsRequest.class));
    }

    private static HttpTransaction tx(String method) {
        return new HttpTransaction(
                "1", EventType.HTTP_TRANSACTION, Direction.INBOUND, Instant.parse("2026-08-27T07:00:00Z"),
                null, null, method, "/x", "/x", null, null, "none", "200", 1, null, null);
    }
}
