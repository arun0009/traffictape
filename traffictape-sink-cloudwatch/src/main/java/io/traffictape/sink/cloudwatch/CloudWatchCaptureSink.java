package io.traffictape.sink.cloudwatch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.traffictape.capture.CaptureBatch;
import io.traffictape.capture.CaptureSink;
import io.traffictape.capture.JsonSupport;
import io.traffictape.model.HttpTransaction;
import io.traffictape.statistics.StatisticsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogGroupRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogStreamRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.InputLogEvent;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.ResourceAlreadyExistsException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CloudWatch Logs sink: one group, one stream per task. JSON lines plus a STATISTICS event per flush.
 */
public final class CloudWatchCaptureSink implements CaptureSink {

    private static final Logger log = LoggerFactory.getLogger(CloudWatchCaptureSink.class);
    private static final long TWO_HOURS_MS = Duration.ofHours(2).toMillis();
    private static final long FOURTEEN_DAYS_MS = Duration.ofDays(14).toMillis();

    private final CloudWatchLogsClient client;
    private final ObjectMapper mapper;
    private final String logGroup;
    private final String logStream;
    private volatile boolean streamReady;

    public CloudWatchCaptureSink(CloudWatchLogsClient client, String logGroup, String logStream) {
        this.client = client;
        this.mapper = JsonSupport.mapper();
        this.logGroup = logGroup;
        this.logStream = logStream;
    }

    public String logGroup() {
        return logGroup;
    }

    public String logStream() {
        return logStream;
    }

    @Override
    public synchronized void write(CaptureBatch batch) {
        if (batch == null) {
            return;
        }
        List<InputLogEvent> events = toLogEvents(batch);
        if (events.isEmpty()) {
            return;
        }
        ensureStream();
        for (List<InputLogEvent> chunk : CloudWatchBatches.pack(events)) {
            client.putLogEvents(PutLogEventsRequest.builder()
                    .logGroupName(logGroup)
                    .logStreamName(logStream)
                    .logEvents(chunk)
                    .build());
        }
    }

    private List<InputLogEvent> toLogEvents(CaptureBatch batch) {
        List<InputLogEvent> events = new ArrayList<>(batch.size() + 1);
        long now = System.currentTimeMillis();
        if (batch.transactions() != null) {
            for (HttpTransaction tx : batch.transactions()) {
                addMessage(events, serialize(tx), clampTimestamp(tx, now));
            }
        }
        addMessage(events, statisticsMessage(batch.statistics()), now);
        events.sort(Comparator.comparingLong(InputLogEvent::timestamp));
        return events;
    }

    private void addMessage(List<InputLogEvent> events, String message, long timestamp) {
        if (message == null || !CloudWatchBatches.fitsAsSingleEvent(message)) {
            if (message != null) {
                log.debug("TrafficTape CloudWatch skipped event over 1MB ({} UTF-8 bytes)",
                        CloudWatchBatches.utf8Bytes(message));
            }
            return;
        }
        events.add(InputLogEvent.builder().timestamp(timestamp).message(message).build());
    }

    private String serialize(HttpTransaction tx) {
        try {
            return mapper.writeValueAsString(tx);
        } catch (JsonProcessingException e) {
            log.debug("TrafficTape CloudWatch skipped unserializable event", e);
            return null;
        }
    }

    /** Snapshot tagged for Insights; gaps/fanout truncated to fit a PutLogEvents item. */
    private String statisticsMessage(StatisticsRegistry.Snapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("schemaVersion", "1");
            body.put("eventType", "STATISTICS");
            body.put("recorder", "traffictape");
            body.put("observedRequests", snapshot.observedRequests());
            body.put("capturedEvents", snapshot.capturedEvents());
            body.put("droppedEvents", snapshot.droppedEvents());
            body.put("lostEvents", snapshot.lostEvents());
            body.put("writeErrors", snapshot.writeErrors());
            body.put("bytesCaptured", snapshot.bytesCaptured());
            body.put("captureReady", snapshot.captureReady());
            body.put("lastNewScenarioAt", snapshot.lastNewScenarioAt());
            body.put("snapshotAt", snapshot.snapshotAt());
            body.put("plateauAfterSeconds", snapshot.plateauAfterSeconds());
            body.put("maxExamplesPerScenario", snapshot.maxExamplesPerScenario());
            body.put("endpoints", snapshot.endpoints());
            body.put("scenarios", snapshot.scenarios());
            body.put("gaps", truncate(snapshot.gaps(), 50));
            body.put("fanout", truncate(snapshot.fanout(), 20));
            return mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            log.debug("TrafficTape CloudWatch skipped statistics event", e);
            return null;
        }
    }

    private static <T> List<T> truncate(List<T> list, int max) {
        if (list == null || list.size() <= max) {
            return list;
        }
        return List.copyOf(list.subList(0, max));
    }

    /**
     * PutLogEvents rejects events older than 14 days or more than 2 hours in the future.
     */
    static long clampTimestamp(HttpTransaction tx, long now) {
        long ts = tx != null && tx.timestamp() != null ? tx.timestamp().toEpochMilli() : now;
        if (ts > now + TWO_HOURS_MS || ts < now - FOURTEEN_DAYS_MS) {
            return now;
        }
        return ts;
    }

    private void ensureStream() {
        if (streamReady) {
            return;
        }
        try {
            client.createLogGroup(CreateLogGroupRequest.builder().logGroupName(logGroup).build());
        } catch (ResourceAlreadyExistsException ignored) {
            // pre-created group
        }
        try {
            client.createLogStream(CreateLogStreamRequest.builder()
                    .logGroupName(logGroup)
                    .logStreamName(logStream)
                    .build());
        } catch (ResourceAlreadyExistsException ignored) {
            // same task restarted
        }
        streamReady = true;
    }
}
