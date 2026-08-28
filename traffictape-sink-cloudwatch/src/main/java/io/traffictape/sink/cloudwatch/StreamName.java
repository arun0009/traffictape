package io.traffictape.sink.cloudwatch;

import io.traffictape.InstanceIds;

/**
 * One CloudWatch stream per JVM so four Fargate tasks do not interleave
 * {@code PutLogEvents} on the same stream.
 */
final class StreamName {

    private StreamName() {
    }

    static String resolve(TrafficTapeCloudWatchProperties properties) {
        String configured = properties.getLogStream() == null ? "" : properties.getLogStream().trim();
        if (!configured.isBlank()) {
            return sanitize(configured);
        }
        return sanitize(instanceId());
    }

    static String instanceId() {
        return InstanceIds.current();
    }

    /**
     * CloudWatch stream names cannot contain {@code :} or {@code *}.
     */
    static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "task";
        }
        String cleaned = value.trim().replace(':', '-').replace('*', '-');
        if (cleaned.length() > 512) {
            cleaned = cleaned.substring(0, 512);
        }
        return cleaned.isBlank() ? "task" : cleaned;
    }
}
