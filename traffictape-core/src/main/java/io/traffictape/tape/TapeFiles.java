package io.traffictape.tape;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.traffictape.TrafficTapeVersion;
import io.traffictape.statistics.StatisticsRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Sidecar files next to events: brief, gaps, fanout, plus shared metadata. */
public final class TapeFiles {

    public static final String GAPS = "gaps.json";
    public static final String FANOUT = "fanout.json";
    public static final String STATISTICS = "statistics.json";
    public static final String METADATA = "metadata.json";

    @FunctionalInterface
    public interface Writer {
        void write(String relativePath, byte[] content, String contentType) throws IOException;
    }

    private TapeFiles() {
    }

    public static Map<String, Object> metadata(
            Map<String, Object> template, Instant captureStart, StatisticsRegistry.Snapshot snapshot) {
        Map<String, Object> meta = new LinkedHashMap<>(template == null ? Map.of() : template);
        meta.put("schemaVersion", "1");
        meta.put("recorder", "traffictape");
        meta.putIfAbsent("recorderVersion", TrafficTapeVersion.get());
        meta.put("captureStart", captureStart.toString());
        meta.put("captureEnd", Instant.now().toString());
        if (snapshot != null) {
            meta.put("totalObservedRequests", snapshot.observedRequests());
            meta.put("totalCapturedEvents", snapshot.capturedEvents());
            meta.put("totalDroppedEvents", snapshot.droppedEvents());
            meta.put("totalLostEvents", snapshot.lostEvents());
            meta.put("writeErrors", snapshot.writeErrors());
            meta.put("bytesCaptured", snapshot.bytesCaptured());
            meta.put("captureReady", snapshot.captureReady());
            meta.put("lastNewScenarioAt", snapshot.lastNewScenarioAt() == null
                    ? null : snapshot.lastNewScenarioAt().toString());
        }
        return meta;
    }

    public static void writeSidecars(StatisticsRegistry.Snapshot snapshot, ObjectMapper mapper, Writer writer)
            throws IOException {
        writer.write(TapeReadme.FILENAME, TapeReadme.TEXT.getBytes(StandardCharsets.UTF_8), "text/markdown");
        if (snapshot == null) {
            return;
        }
        writer.write(GAPS, pretty(mapper, snapshot.gaps()), "application/json");
        writer.write(FANOUT, pretty(mapper, snapshot.fanout()), "application/json");
    }

    public static void writeStatistics(StatisticsRegistry.Snapshot snapshot, ObjectMapper mapper, Writer writer)
            throws IOException {
        if (snapshot == null) {
            return;
        }
        writer.write(STATISTICS, pretty(mapper, snapshot), "application/json");
    }

    public static byte[] pretty(ObjectMapper mapper, Object value) throws IOException {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
    }
}
