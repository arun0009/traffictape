package io.traffictape.spring.actuate;

import io.traffictape.statistics.StatisticsRegistry;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Answers the one operational question capture raises: can I turn this off yet?
 *
 * <p>Without it the answer is only in the corpus itself, as the trailing statistics event of a
 * gzipped file — which means unpacking a corpus to decide whether to keep filling it.
 *
 * <p>Exposed at {@code /actuator/traffictape}. Nothing here contains request or response bodies:
 * it is route templates and counts, so it is safe to expose wherever the rest of Actuator is.
 */
@Endpoint(id = "traffictape")
public class TrafficTapeEndpoint {

    /** Enough to act on without turning the response into the corpus. */
    private static final int MAX_LISTED = 20;

    private final StatisticsRegistry statistics;

    public TrafficTapeEndpoint(StatisticsRegistry statistics) {
        this.statistics = statistics;
    }

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
        // Two independent conditions: no newly discovered behaviour for a while, and every
        // scenario found has the examples a mock needs. Either one alone is misleading.
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
        out.put("writeErrors", statistics.writeErrors());
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
