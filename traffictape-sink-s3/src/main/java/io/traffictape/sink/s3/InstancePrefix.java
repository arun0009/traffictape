package io.traffictape.sink.s3;

import java.net.InetAddress;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

/**
 * Builds an S3 key prefix. Four Fargate tasks must not share {@code events-000001.jsonl.gz}.
 */
final class InstancePrefix {

    private InstancePrefix() {
    }

    static String resolve(TrafficTapeS3Properties properties, String serviceName) {
        String base = firstNonBlank(trimSlashes(properties.getPrefix()), sanitize(serviceName), "application");
        if (!properties.isUniquePerInstance()) {
            return base;
        }
        return base + "/" + LocalDate.now(ZoneOffset.UTC) + "/" + sanitize(instanceId());
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

    static String trimSlashes(String value) {
        if (value == null) {
            return "";
        }
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '/') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(start, end).trim();
    }

    static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().replaceAll("[^a-zA-Z0-9._-]", "-").toLowerCase(Locale.ROOT);
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
