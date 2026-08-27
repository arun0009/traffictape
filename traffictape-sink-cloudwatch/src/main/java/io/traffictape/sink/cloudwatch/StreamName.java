package io.traffictape.sink.cloudwatch;

import java.net.InetAddress;
import java.util.UUID;

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
        String host = firstNonBlank(
                System.getenv("HOSTNAME"),
                System.getenv("COMPUTERNAME"),
                localHostName());
        if (host == null || host.isBlank() || "localhost".equalsIgnoreCase(host)) {
            return UUID.randomUUID().toString().substring(0, 8);
        }
        return host;
    }

    private static String localHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return null;
        }
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

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
