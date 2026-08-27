package io.traffictape.statistics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/** In-flight outbound hops keyed by inbound exchange, rolled up per inbound scenario. */
final class FanoutRegistry {

    static final int MAX_IN_FLIGHT = 8_000;
    static final int MAX_HOPS = 16;
    static final int MAX_PATTERNS = 40;
    static final int MAX_HOP_VARIANTS = 8;

    private final ConcurrentHashMap<String, List<Hop>> inFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, InboundFanout> byInbound = new ConcurrentHashMap<>();

    void hop(String exchangeId, Integer sequence, String destination, String method, String route, int status) {
        if (exchangeId == null || exchangeId.isBlank()) {
            return;
        }
        if (inFlight.size() >= MAX_IN_FLIGHT && !inFlight.containsKey(exchangeId)) {
            return;
        }
        List<Hop> hops = inFlight.computeIfAbsent(exchangeId, k -> new ArrayList<>());
        synchronized (hops) {
            if (hops.size() >= MAX_HOPS) {
                return;
            }
            hops.add(new Hop(sequence == null ? hops.size() + 1 : sequence, destination, method, route, status));
        }
    }

    void complete(String exchangeId, String inboundFingerprint, String inboundLabel, String method, String route) {
        if (exchangeId == null || inboundFingerprint == null) {
            return;
        }
        List<Hop> hops = inFlight.remove(exchangeId);
        String key;
        if (hops == null) {
            key = "(none)";
        } else {
            synchronized (hops) {
                key = signature(hops);
            }
        }
        InboundFanout fanout = byInbound.computeIfAbsent(inboundFingerprint,
                k -> new InboundFanout(inboundFingerprint, inboundLabel, method, route));
        fanout.patterns.computeIfAbsent(key, k -> new LongAdder()).increment();
    }

    List<StatisticsRegistry.FanoutPattern> snapshot() {
        List<StatisticsRegistry.FanoutPattern> out = new ArrayList<>();
        byInbound.values().forEach(inbound -> {
            List<StatisticsRegistry.FanoutHop> patterns = new ArrayList<>();
            inbound.patterns.forEach((sig, n) -> patterns.add(new StatisticsRegistry.FanoutHop(sig, n.sum())));
            patterns.sort(Comparator.comparingLong(StatisticsRegistry.FanoutHop::count).reversed());
            long total = patterns.stream().mapToLong(StatisticsRegistry.FanoutHop::count).sum();
            out.add(new StatisticsRegistry.FanoutPattern(
                    inbound.fingerprint,
                    inbound.label,
                    inbound.method,
                    inbound.route,
                    total,
                    patterns.size() > MAX_HOP_VARIANTS ? List.copyOf(patterns.subList(0, MAX_HOP_VARIANTS))
                            : List.copyOf(patterns)));
        });
        out.sort(Comparator.comparingLong(StatisticsRegistry.FanoutPattern::observed).reversed());
        return out.size() > MAX_PATTERNS ? List.copyOf(out.subList(0, MAX_PATTERNS)) : List.copyOf(out);
    }

    private static String signature(List<Hop> hops) {
        if (hops == null || hops.isEmpty()) {
            return "(none)";
        }
        return hops.stream()
                .sorted(Comparator.comparingInt(Hop::sequence))
                .map(h -> h.sequence() + ". " + h.method() + " " + h.route() + " " + h.status() + " → "
                        + (h.destination() == null || h.destination().isBlank() ? "unknown" : h.destination()))
                .collect(Collectors.joining(" > "));
    }

    private record Hop(int sequence, String destination, String method, String route, int status) {
    }

    private static final class InboundFanout {
        private final String fingerprint;
        private final String label;
        private final String method;
        private final String route;
        private final ConcurrentHashMap<String, LongAdder> patterns = new ConcurrentHashMap<>();

        private InboundFanout(String fingerprint, String label, String method, String route) {
            this.fingerprint = fingerprint;
            this.label = label;
            this.method = method;
            this.route = route;
        }
    }
}
