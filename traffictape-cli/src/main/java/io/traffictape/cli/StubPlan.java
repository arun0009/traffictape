package io.traffictape.cli;

import io.traffictape.model.HttpTransaction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The set of outbound scenarios that can be told apart by a request matcher, resolved once so
 * every output format agrees on what is stubbed and what was dropped.
 *
 * <p>Two scenarios that differ only by response — a 200 and a 404 on the same request — cannot be
 * distinguished by any matcher. Keeping both would leave one permanently unreachable, so the
 * success case wins and the other is reported.
 */
final class StubPlan {

    record Stub(String scenarioId, HttpTransaction transaction, List<String> bodyFields) {

        String label() {
            HttpTransaction tx = transaction;
            if (tx.fingerprints() != null && tx.fingerprints().scenario() != null
                    && tx.fingerprints().scenario().label() != null) {
                return tx.fingerprints().scenario().label();
            }
            return tx.method() + " " + tx.route();
        }

        String route() {
            return transaction.route() == null ? transaction.path() : transaction.route();
        }

        String destination() {
            return transaction.destination() == null ? "unknown" : transaction.destination();
        }
    }

    private final List<Stub> stubs;
    private final List<String> collisions;

    private StubPlan(List<Stub> stubs, List<String> collisions) {
        this.stubs = stubs;
        this.collisions = collisions;
    }

    static StubPlan of(Corpus corpus) {
        Map<String, Stub> chosen = new LinkedHashMap<>();
        List<String> collisions = new ArrayList<>();

        corpus.outboundScenarios().forEach((scenarioId, tx) -> {
            List<String> bodyFields = corpus.needsBodyMatching(tx)
                    ? StubSupport.topLevelJsonFields(tx.request() == null ? null : tx.request().body())
                    : List.of();
            Stub candidate = new Stub(scenarioId, tx, bodyFields);
            String key = matcherKey(candidate);
            Stub current = chosen.get(key);
            if (current == null) {
                chosen.put(key, candidate);
                return;
            }
            Stub keep = StubSupport.preferOver(tx, current.transaction()) ? candidate : current;
            Stub drop = keep == candidate ? current : candidate;
            chosen.put(key, keep);
            collisions.add(drop.label() + " -> same request as " + keep.label()
                    + "; only the latter was written");
        });

        return new StubPlan(List.copyOf(chosen.values()), List.copyOf(collisions));
    }

    private static String matcherKey(Stub stub) {
        HttpTransaction tx = stub.transaction();
        StringBuilder key = new StringBuilder()
                .append(stub.destination()).append(' ')
                .append(tx.method()).append(' ')
                .append(stub.route());
        if (tx.query() != null) {
            tx.query().keySet().stream().sorted().forEach(name -> key.append(" q:").append(name));
        }
        stub.bodyFields().stream().sorted().forEach(field -> key.append(" b:").append(field));
        return key.toString();
    }

    List<Stub> stubs() {
        return stubs;
    }

    List<String> collisions() {
        return collisions;
    }
}
