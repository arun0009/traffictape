package io.traffictape.statistics;

import io.traffictape.model.Direction;
import io.traffictape.model.Fingerprint;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/** Bounded in-memory statistics. Counts continue after example capture stops. */
public final class StatisticsRegistry {

    public static final String OVERFLOW = "overflow";
    public static final Duration DEFAULT_PLATEAU_AFTER = Duration.ofHours(6);
    static final int MAX_GAPS = 200;

    private final int maxUniqueFingerprints;
    private final int maxExamplesPerScenario;
    private final Duration plateauAfter;
    private final Clock clock;
    private final ConcurrentHashMap<String, MutableStats> endpoints = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MutableStats> scenarios = new ConcurrentHashMap<>();
    private final FanoutRegistry fanout = new FanoutRegistry();
    private final AtomicReference<Instant> lastNewScenarioAt = new AtomicReference<>();
    private final LongAdder observed = new LongAdder();
    private final LongAdder captured = new LongAdder();
    private final LongAdder dropped = new LongAdder();
    private final LongAdder bytesCaptured = new LongAdder();
    private final LongAdder writeErrors = new LongAdder();

    public StatisticsRegistry(int maxUniqueFingerprints) {
        this(maxUniqueFingerprints, 50, DEFAULT_PLATEAU_AFTER, Clock.systemUTC());
    }

    public StatisticsRegistry(int maxUniqueFingerprints, int maxExamplesPerScenario, Duration plateauAfter) {
        this(maxUniqueFingerprints, maxExamplesPerScenario, plateauAfter, Clock.systemUTC());
    }

    public StatisticsRegistry(
            int maxUniqueFingerprints,
            int maxExamplesPerScenario,
            Duration plateauAfter,
            Clock clock) {
        this.maxUniqueFingerprints = Math.max(16, maxUniqueFingerprints);
        this.maxExamplesPerScenario = Math.max(1, maxExamplesPerScenario);
        this.plateauAfter = plateauAfter == null ? DEFAULT_PLATEAU_AFTER : plateauAfter;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public void recordObservation(
            Direction direction,
            String method,
            String route,
            Fingerprint endpoint,
            Fingerprint scenario,
            int status,
            long latencyMs,
            long requestBytes,
            long responseBytes,
            Instant now) {
        observed.increment();
        MutableStats endpointStats = stats(endpoints, endpoint, direction, method, route);
        endpointStats.record(status, latencyMs, requestBytes, responseBytes, now);
        MutableStats scenarioStats = stats(scenarios, scenario, direction, method, route);
        boolean first = scenarioStats.record(status, latencyMs, requestBytes, responseBytes, now);
        if (first && !OVERFLOW.equals(scenarioStats.fingerprint())) {
            endpointStats.incrementScenarios();
            lastNewScenarioAt.set(now);
        }
    }

    public void recordFanoutHop(
            String exchangeId,
            Integer sequence,
            String destination,
            String method,
            String route,
            int status) {
        fanout.hop(exchangeId, sequence, destination, method, route, status);
    }

    public void completeFanout(
            String exchangeId,
            String inboundFingerprint,
            String inboundLabel,
            String method,
            String route) {
        fanout.complete(exchangeId, inboundFingerprint, inboundLabel, method, route);
    }

    public void recordCaptured(Fingerprint scenario, long bytes) {
        captured.increment();
        bytesCaptured.add(Math.max(0, bytes));
        MutableStats stats = scenarios.get(scenario.id());
        if (stats != null) {
            stats.incrementCaptured();
        }
    }

    public void recordDropped() {
        dropped.increment();
    }

    public void recordWriteError() {
        writeErrors.increment();
    }

    public long observed() {
        return observed.sum();
    }

    public long captured() {
        return captured.sum();
    }

    public long dropped() {
        return dropped.sum();
    }

    public long bytesCaptured() {
        return bytesCaptured.sum();
    }

    public long writeErrors() {
        return writeErrors.sum();
    }

    public int uniqueEndpoints() {
        return endpoints.size();
    }

    public int uniqueScenarios() {
        return scenarios.size();
    }

    public Snapshot snapshot() {
        Instant lastNew = lastNewScenarioAt.get();
        Instant snapshotAt = clock.instant();
        List<MutableStats.Snapshot> scenarioSnaps = ranked(scenarios);
        return new Snapshot(
                observed.sum(),
                captured.sum(),
                dropped.sum(),
                bytesCaptured.sum(),
                ranked(endpoints),
                scenarioSnaps,
                lastNew,
                snapshotAt,
                plateauAfter.toSeconds(),
                lastNew != null && !lastNew.plus(plateauAfter).isAfter(snapshotAt),
                maxExamplesPerScenario,
                gaps(scenarioSnaps),
                fanout.snapshot());
    }

    private List<Gap> gaps(List<MutableStats.Snapshot> scenarioSnaps) {
        List<Gap> gaps = new ArrayList<>();
        for (MutableStats.Snapshot s : scenarioSnaps) {
            if (OVERFLOW.equals(s.fingerprint())) {
                continue;
            }
            gaps.add(new Gap(
                    s.fingerprint(),
                    s.label(),
                    s.direction(),
                    s.method(),
                    s.route(),
                    s.count(),
                    s.capturedExamples(),
                    s.capturedExamples() >= Math.min(s.count(), maxExamplesPerScenario)));
        }
        gaps.sort(Comparator.comparing(Gap::bodiesComplete)
                .thenComparing(Comparator.comparingLong(Gap::count).reversed()));
        return take(gaps, MAX_GAPS);
    }

    private List<MutableStats.Snapshot> ranked(ConcurrentHashMap<String, MutableStats> map) {
        List<MutableStats.Snapshot> snaps = new ArrayList<>(map.size());
        map.values().forEach(s -> snaps.add(s.snapshot()));
        snaps.sort(Comparator.comparingLong(MutableStats.Snapshot::count).reversed());
        return snaps;
    }

    private static <T> List<T> take(List<T> list, int n) {
        return list.size() <= n ? List.copyOf(list) : List.copyOf(list.subList(0, n));
    }

    private MutableStats stats(
            ConcurrentHashMap<String, MutableStats> map,
            Fingerprint fingerprint,
            Direction direction,
            String method,
            String route) {
        if (map.size() >= maxUniqueFingerprints && !map.containsKey(fingerprint.id())) {
            return map.computeIfAbsent(OVERFLOW, k ->
                    new MutableStats(OVERFLOW, "OVERFLOW", direction.name(), method, route));
        }
        return map.computeIfAbsent(fingerprint.id(), k ->
                new MutableStats(fingerprint.id(), fingerprint.label(), direction.name(), method, route));
    }

    public record Snapshot(
            long observedRequests,
            long capturedEvents,
            long droppedEvents,
            long bytesCaptured,
            List<MutableStats.Snapshot> endpoints,
            List<MutableStats.Snapshot> scenarios,
            Instant lastNewScenarioAt,
            Instant snapshotAt,
            long plateauAfterSeconds,
            boolean captureReady,
            int maxExamplesPerScenario,
            List<Gap> gaps,
            List<FanoutPattern> fanout
    ) {
    }

    /** Ranked scenarios; incomplete bodies first. bodiesComplete = capturedExamples >= min(count, N). */
    public record Gap(
            String fingerprint,
            String label,
            String direction,
            String method,
            String route,
            long count,
            long capturedExamples,
            boolean bodiesComplete
    ) {
    }

    public record FanoutPattern(
            String inboundFingerprint,
            String inboundLabel,
            String method,
            String route,
            long observed,
            List<FanoutHop> patterns
    ) {
    }

    public record FanoutHop(String hops, long count) {
    }
}
