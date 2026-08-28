package io.traffictape.cli;

import io.traffictape.model.Direction;
import io.traffictape.model.HttpTransaction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recorded events reduced to one example per scenario, plus the inbound-to-outbound
 * graph rebuilt from {@code parentExchangeId}.
 *
 * <p>The sampler budget is per JVM, so four tasks can record the same scenario four times;
 * generate keeps one.
 */
final class Tape {

    private final Map<String, HttpTransaction> inboundScenarios;
    private final Map<String, HttpTransaction> outboundScenarios;
    private final Map<String, Set<String>> inboundDependencies;
    private final Map<String, Integer> scenariosPerEndpoint;
    private final int totalEvents;

    private Tape(Map<String, HttpTransaction> inboundScenarios,
                   Map<String, HttpTransaction> outboundScenarios,
                   Map<String, Set<String>> inboundDependencies,
                   Map<String, Integer> scenariosPerEndpoint,
                   int totalEvents) {
        this.inboundScenarios = inboundScenarios;
        this.outboundScenarios = outboundScenarios;
        this.inboundDependencies = inboundDependencies;
        this.scenariosPerEndpoint = scenariosPerEndpoint;
        this.totalEvents = totalEvents;
    }

    static Tape index(List<HttpTransaction> events) {
        Map<String, HttpTransaction> inbound = new LinkedHashMap<>();
        Map<String, HttpTransaction> outbound = new LinkedHashMap<>();
        Map<String, String> exchangeToInboundScenario = new HashMap<>();
        Map<String, List<HttpTransaction>> childrenByParent = new HashMap<>();

        for (HttpTransaction tx : events) {
            String scenario = scenarioKey(tx);
            if (tx.direction() == Direction.OUTBOUND) {
                outbound.putIfAbsent(scenario, tx);
                String parent = tx.correlation() == null ? null : tx.correlation().parentExchangeId();
                if (parent != null) {
                    childrenByParent.computeIfAbsent(parent, k -> new ArrayList<>()).add(tx);
                }
            } else {
                inbound.putIfAbsent(scenario, tx);
                String exchangeId = tx.correlation() == null ? null : tx.correlation().exchangeId();
                if (exchangeId != null) {
                    exchangeToInboundScenario.put(exchangeId, scenario);
                }
            }
        }

        Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        for (Map.Entry<String, List<HttpTransaction>> entry : childrenByParent.entrySet()) {
            String inboundScenario = exchangeToInboundScenario.get(entry.getKey());
            if (inboundScenario == null) {
                continue;
            }
            List<HttpTransaction> children = new ArrayList<>(entry.getValue());
            children.sort(Comparator.comparing(tx -> sequenceOf(tx)));
            Set<String> ids = dependencies.computeIfAbsent(inboundScenario, k -> new LinkedHashSet<>());
            for (HttpTransaction child : children) {
                ids.add(scenarioKey(child));
            }
        }

        Map<String, Set<String>> byEndpoint = new HashMap<>();
        for (HttpTransaction tx : outbound.values()) {
            byEndpoint.computeIfAbsent(endpointKey(tx), k -> new HashSet<>()).add(scenarioKey(tx));
        }
        Map<String, Integer> counts = new HashMap<>();
        byEndpoint.forEach((endpoint, scenarios) -> counts.put(endpoint, scenarios.size()));

        return new Tape(inbound, outbound, dependencies, counts, events.size());
    }

    private static int sequenceOf(HttpTransaction tx) {
        if (tx.correlation() == null || tx.correlation().sequence() == null) {
            return Integer.MAX_VALUE;
        }
        return tx.correlation().sequence();
    }

    /** Falls back to the label, then to the route, if the event has no scenario id. */
    static String scenarioKey(HttpTransaction tx) {
        String id = tx.scenarioFingerprintId();
        if (id != null) {
            return id;
        }
        return tx.direction() + " " + tx.method() + " " + tx.route() + " "
                + tx.requestShape() + " " + tx.responseCharacteristic();
    }

    static String endpointKey(HttpTransaction tx) {
        String id = tx.endpointFingerprintId();
        if (id != null) {
            return id;
        }
        return tx.direction() + " " + tx.method() + " " + tx.route();
    }

    Map<String, HttpTransaction> inboundScenarios() {
        return inboundScenarios;
    }

    Map<String, HttpTransaction> outboundScenarios() {
        return outboundScenarios;
    }

    Set<String> dependenciesOf(String inboundScenario) {
        return inboundDependencies.getOrDefault(inboundScenario, Set.of());
    }

    /**
     * Whether an endpoint has more than one scenario, and therefore needs request-body matching to
     * pick the right stub. Over-constraining a single-scenario endpoint only makes stubs brittle.
     */
    boolean needsBodyMatching(HttpTransaction tx) {
        return scenariosPerEndpoint.getOrDefault(endpointKey(tx), 1) > 1;
    }

    int totalEvents() {
        return totalEvents;
    }
}
