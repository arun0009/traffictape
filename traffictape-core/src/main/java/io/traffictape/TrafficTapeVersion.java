package io.traffictape;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Recorder version written into event metadata. Read once from a build-filtered resource.
 */
public final class TrafficTapeVersion {

    private static final String UNKNOWN = "unknown";
    private static final String VALUE = read();

    private TrafficTapeVersion() {
    }

    public static String get() {
        return VALUE;
    }

    private static String read() {
        try (InputStream in = TrafficTapeVersion.class.getResourceAsStream("version.properties")) {
            if (in == null) {
                return UNKNOWN;
            }
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("version");
            if (version == null || version.isBlank() || version.startsWith("${")) {
                return UNKNOWN;
            }
            return version;
        } catch (IOException | RuntimeException e) {
            return UNKNOWN;
        }
    }
}
