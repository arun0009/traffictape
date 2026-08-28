package io.traffictape.spring.actuate;

import io.traffictape.capture.CaptureSink;
import io.traffictape.statistics.StatisticsRegistry;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /actuator/traffictape} — whether capture can be turned off.
 * Route templates and counts only; no bodies.
 */
@Endpoint(id = "traffictape")
public class TrafficTapeEndpoint {

    private static final int MAX_LISTED = 20;

    private final StatisticsRegistry statistics;
    private final CaptureSink sink;

    /**
     * @param statistics source of the reported counts and plateau state
     * @param sink capture sink to report as disabled or not; {@code null} means no sink
     */
    public TrafficTapeEndpoint(StatisticsRegistry statistics, CaptureSink sink) {
        this.statistics = statistics;
        this.sink = sink == null ? CaptureSink.NOOP : sink;
    }

    /**
     * Reports whether the corpus looks complete enough to stop capturing. {@code ready} is
     * {@code true} once capture has plateaued and no scenario is still missing bodies.
     *
     * @return capture readiness, counts, and up to 20 incomplete scenarios
     */
    @ReadOperation
    public Map<String, Object> capture() {
        StatisticsRegistry.Snapshot snapshot = statistics.snapshot();
        List<StatisticsRegistry.Gap> incomplete = new ArrayList<>();
        for (StatisticsRegistry.Gap gap : snapshot.gaps()) {
            if (!gap.bodiesComplete()) {
                incomplete.add(gap);
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ready", snapshot.captureReady() && incomplete.isEmpty());
        out.put("plateauReached", snapshot.captureReady());
        out.put("scenariosMissingExamples", incomplete.size());
        out.put("lastNewScenarioAt", snapshot.lastNewScenarioAt());
        out.put("plateauAfterSeconds", snapshot.plateauAfterSeconds());
        out.put("maxExamplesPerScenario", snapshot.maxExamplesPerScenario());
        out.put("uniqueEndpoints", statistics.uniqueEndpoints());
        out.put("uniqueScenarios", snapshot.scenarios().size());
        out.put("observedRequests", snapshot.observedRequests());
        out.put("capturedEvents", snapshot.capturedEvents());
        out.put("droppedEvents", snapshot.droppedEvents());
        out.put("lostEvents", snapshot.lostEvents());
        out.put("writeErrors", snapshot.writeErrors());
        out.put("sinkDisabled", sink.isDisabled());
        out.put("bytesCaptured", snapshot.bytesCaptured());
        out.put("incomplete", describe(incomplete));
        return out;
    }

    private static List<Map<String, Object>> describe(List<StatisticsRegistry.Gap> gaps) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (StatisticsRegistry.Gap gap : gaps.size() > MAX_LISTED ? gaps.subList(0, MAX_LISTED) : gaps) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", gap.label());
            row.put("direction", gap.direction());
            row.put("method", gap.method());
            row.put("route", gap.route());
            row.put("observed", gap.count());
            row.put("examples", gap.capturedExamples());
            out.add(row);
        }
        return out;
    }
}
