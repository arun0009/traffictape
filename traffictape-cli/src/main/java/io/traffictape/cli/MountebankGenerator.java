package io.traffictape.cli;

import io.traffictape.model.BodyCapture;
import io.traffictape.model.BodyEncoding;
import io.traffictape.model.HttpTransaction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns outbound scenarios into Mountebank imposters, one per outbound destination.
 *
 * <p>Ports are assigned from {@code --base-port} rather than reused from the observed destination,
 * because two destinations routinely share a port on different hosts. The returned port map is what
 * you point the application at.
 */
final class MountebankGenerator {

    record Result(List<Map<String, Object>> imposters, Map<String, Integer> ports) {
    }

    Result generate(StubPlan plan, int basePort) {
        Map<String, List<StubPlan.Stub>> byDestination = new TreeMap<>();
        for (StubPlan.Stub stub : plan.stubs()) {
            byDestination.computeIfAbsent(stub.destination(), k -> new ArrayList<>()).add(stub);
        }

        Map<String, Integer> ports = new LinkedHashMap<>();
        List<Map<String, Object>> imposters = new ArrayList<>();
        int port = basePort;
        for (Map.Entry<String, List<StubPlan.Stub>> entry : byDestination.entrySet()) {
            ports.put(entry.getKey(), port);

            List<StubPlan.Stub> ordered = new ArrayList<>(entry.getValue());
            // Mountebank serves the first matching stub, so shape-matched ones must come first.
            ordered.sort((a, b) -> Integer.compare(b.bodyFields().size(), a.bodyFields().size()));

            List<Map<String, Object>> stubs = new ArrayList<>();
            for (StubPlan.Stub stub : ordered) {
                stubs.add(stub(stub));
            }

            Map<String, Object> imposter = new LinkedHashMap<>();
            imposter.put("port", port);
            imposter.put("protocol", "http");
            imposter.put("name", entry.getKey());
            imposter.put("stubs", stubs);
            imposters.add(imposter);
            port++;
        }
        return new Result(imposters, ports);
    }

    private Map<String, Object> stub(StubPlan.Stub stub) {
        HttpTransaction tx = stub.transaction();

        List<Map<String, Object>> predicates = new ArrayList<>();
        predicates.add(Map.of("equals", Map.of("method", tx.method())));

        String route = stub.route();
        if (StubSupport.isTemplated(route)) {
            predicates.add(Map.of("matches",
                    Map.of("path", "^" + StubSupport.routeToRegex(route) + "$")));
        } else {
            predicates.add(Map.of("equals", Map.of("path", route)));
        }
        if (tx.query() != null && !tx.query().isEmpty()) {
            Map<String, Object> present = new LinkedHashMap<>();
            tx.query().keySet().forEach(name -> present.put(name, true));
            predicates.add(Map.of("exists", Map.of("query", present)));
        }
        if (!stub.bodyFields().isEmpty()) {
            Map<String, Object> present = new LinkedHashMap<>();
            stub.bodyFields().forEach(field -> present.put(field, true));
            predicates.add(Map.of("exists", Map.of("body", present)));
        }

        Map<String, Object> is = new LinkedHashMap<>();
        is.put("statusCode", tx.response() == null ? 200 : tx.response().status());
        Map<String, Object> headers = StubSupport.responseHeaders(
                tx.response() == null ? null : tx.response().headers());
        if (!headers.isEmpty()) {
            is.put("headers", headers);
        }
        BodyCapture body = tx.response() == null ? null : tx.response().body();
        if (body != null && body.body() != null
                && (body.encoding() == BodyEncoding.JSON || body.encoding() == BodyEncoding.TEXT)) {
            is.put("body", body.body());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", stub.label());
        result.put("predicates", predicates);
        result.put("responses", List.of(Map.of("is", is)));
        return result;
    }
}
