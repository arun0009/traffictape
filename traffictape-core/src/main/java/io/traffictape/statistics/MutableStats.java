package io.traffictape.statistics;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class MutableStats {

    private final String fingerprint;
    private final String label;
    private final String direction;
    private final String method;
    private final String route;
    private final LongAdder count = new LongAdder();
    private final LongAdder capturedExamples = new LongAdder();
    private final ConcurrentHashMap<Integer, LongAdder> statuses = new ConcurrentHashMap<>();
    private final AtomicLong requestBytes = new AtomicLong();
    private final AtomicLong responseBytes = new AtomicLong();
    private final AtomicLong latencySum = new AtomicLong();
    private final AtomicLong latencyMin = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong latencyMax = new AtomicLong();
    private final AtomicLong firstSeen = new AtomicLong(0);
    private final AtomicLong lastSeen = new AtomicLong(0);
    private final AtomicInteger uniqueScenarios = new AtomicInteger();

    public MutableStats(String fingerprint, String label, String direction, String method, String route) {
        this.fingerprint = fingerprint;
        this.label = label;
        this.direction = direction;
        this.method = method;
        this.route = route;
    }

    public String fingerprint() {
        return fingerprint;
    }

    /** @return true on the first observation for this fingerprint */
    public boolean record(int status, long latencyMs, long reqBytes, long respBytes, Instant now) {
        count.increment();
        statuses.computeIfAbsent(status, s -> new LongAdder()).increment();
        requestBytes.addAndGet(Math.max(0, reqBytes));
        responseBytes.addAndGet(Math.max(0, respBytes));
        latencySum.addAndGet(Math.max(0, latencyMs));
        latencyMin.accumulateAndGet(latencyMs, Math::min);
        latencyMax.accumulateAndGet(latencyMs, Math::max);
        long epoch = now.toEpochMilli();
        boolean first = firstSeen.compareAndSet(0, epoch);
        lastSeen.set(epoch);
        return first;
    }

    public void incrementCaptured() {
        capturedExamples.increment();
    }

    public void incrementScenarios() {
        uniqueScenarios.incrementAndGet();
    }

    public long count() {
        return count.sum();
    }

    public long capturedExamples() {
        return capturedExamples.sum();
    }

    public Snapshot snapshot() {
        Map<String, Long> statusMap = new java.util.TreeMap<>();
        statuses.forEach((code, adder) -> statusMap.put(Integer.toString(code), adder.sum()));
        long n = count.sum();
        double avgLatency = n == 0 ? 0 : (double) latencySum.get() / n;
        return new Snapshot(
                fingerprint,
                label,
                direction,
                method,
                route,
                n,
                statusMap,
                capturedExamples.sum(),
                uniqueScenarios.get(),
                firstSeen.get() == 0 ? null : Instant.ofEpochMilli(firstSeen.get()),
                lastSeen.get() == 0 ? null : Instant.ofEpochMilli(lastSeen.get()),
                requestBytes.get(),
                responseBytes.get(),
                latencyMin.get() == Long.MAX_VALUE ? 0 : latencyMin.get(),
                latencyMax.get(),
                avgLatency
        );
    }

    public record Snapshot(
            String fingerprint,
            String label,
            String direction,
            String method,
            String route,
            long count,
            Map<String, Long> statuses,
            long capturedExamples,
            int uniqueScenarios,
            Instant firstSeen,
            Instant lastSeen,
            long requestBytes,
            long responseBytes,
            long latencyMinMs,
            long latencyMaxMs,
            double latencyAvgMs
    ) {
    }
}
