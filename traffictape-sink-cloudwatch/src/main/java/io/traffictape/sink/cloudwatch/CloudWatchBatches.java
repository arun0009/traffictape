package io.traffictape.sink.cloudwatch;

import software.amazon.awssdk.services.cloudwatchlogs.model.InputLogEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Packs log events into {@code PutLogEvents} batches.
 *
 * <p>CloudWatch limits: 1&nbsp;MB per event, 1&nbsp;MB per batch (UTF-8 bytes
 * plus 26 bytes overhead each), 10_000 events per batch.
 */
final class CloudWatchBatches {

    static final int MAX_BATCH_BYTES = 1_048_576;
    static final int EVENT_OVERHEAD = 26;
    static final int MAX_EVENT_BYTES = 1_048_576;
    static final int MAX_EVENTS_PER_BATCH = 10_000;

    private CloudWatchBatches() {
    }

    static int utf8Bytes(String message) {
        if (message == null) {
            return 0;
        }
        return message.getBytes(StandardCharsets.UTF_8).length;
    }

    static boolean fitsAsSingleEvent(String message) {
        int size = utf8Bytes(message);
        return size > 0 && size + EVENT_OVERHEAD <= MAX_EVENT_BYTES;
    }

    static List<List<InputLogEvent>> pack(List<InputLogEvent> events) {
        List<List<InputLogEvent>> batches = new ArrayList<>();
        if (events == null || events.isEmpty()) {
            return batches;
        }
        List<InputLogEvent> current = new ArrayList<>();
        int bytes = 0;
        for (InputLogEvent event : events) {
            int size = utf8Bytes(event.message()) + EVENT_OVERHEAD;
            boolean overflow = !current.isEmpty()
                    && (current.size() >= MAX_EVENTS_PER_BATCH || bytes + size > MAX_BATCH_BYTES);
            if (overflow) {
                batches.add(current);
                current = new ArrayList<>();
                bytes = 0;
            }
            current.add(event);
            bytes += size;
        }
        if (!current.isEmpty()) {
            batches.add(current);
        }
        return batches;
    }
}
