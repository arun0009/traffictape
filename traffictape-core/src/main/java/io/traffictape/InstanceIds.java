package io.traffictape;

import java.net.InetAddress;
import java.util.UUID;

/** Hostname used as a per-task suffix for S3 prefixes and CloudWatch streams. */
public final class InstanceIds {

    private InstanceIds() {
    }

    public static String current() {
        String host = firstNonBlank(
                System.getenv("HOSTNAME"),
                System.getenv("COMPUTERNAME"),
                localHostName());
        if (host == null || host.isBlank() || "localhost".equalsIgnoreCase(host)) {
            return UUID.randomUUID().toString().substring(0, 8);
        }
        return host;
    }

    public static String firstNonBlank(String... values) {
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

    private static String localHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return null;
        }
    }
}
