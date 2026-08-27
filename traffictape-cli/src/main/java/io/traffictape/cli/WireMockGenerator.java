package io.traffictape.cli;

import io.traffictape.model.BodyCapture;
import io.traffictape.model.BodyEncoding;
import io.traffictape.model.HttpTransaction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns outbound scenarios into WireMock stub mappings — the calls the recorded application made,
 * which are the ones a hermetic test needs to stub out.
 */
final class WireMockGenerator {

    record Mapping(String fileName, Map<String, Object> json) {
    }

    List<Mapping> generate(StubPlan plan) {
        Set<String> usedNames = new HashSet<>();
        List<Mapping> mappings = new ArrayList<>();
        for (StubPlan.Stub stub : plan.stubs()) {
            mappings.add(new Mapping(uniqueFileName(stub, usedNames), stub(stub)));
        }
        return mappings;
    }

    private Map<String, Object> stub(StubPlan.Stub stub) {
        HttpTransaction tx = stub.transaction();

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", tx.method());
        String route = stub.route();
        if (StubSupport.isTemplated(route)) {
            request.put("urlPathPattern", StubSupport.routeToRegex(route));
        } else {
            request.put("urlPath", route);
        }
        if (tx.query() != null && !tx.query().isEmpty()) {
            Map<String, Object> params = new LinkedHashMap<>();
            tx.query().keySet().forEach(name -> params.put(name, Map.of("matches", ".*")));
            request.put("queryParameters", params);
        }
        if (!stub.bodyFields().isEmpty()) {
            List<Map<String, Object>> patterns = new ArrayList<>();
            for (String field : stub.bodyFields()) {
                patterns.add(Map.of("matchesJsonPath", "$.['" + field + "']"));
            }
            request.put("bodyPatterns", patterns);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", tx.response() == null ? 200 : tx.response().status());
        Map<String, Object> headers = StubSupport.responseHeaders(
                tx.response() == null ? null : tx.response().headers());
        if (!headers.isEmpty()) {
            response.put("headers", headers);
        }
        BodyCapture body = tx.response() == null ? null : tx.response().body();
        if (body != null && body.body() != null) {
            if (body.encoding() == BodyEncoding.JSON) {
                response.put("jsonBody", body.body());
            } else if (body.encoding() == BodyEncoding.TEXT) {
                response.put("body", body.body().toString());
            }
        }

        Map<String, Object> provenance = new LinkedHashMap<>();
        provenance.put("scenario", stub.scenarioId());
        provenance.put("label", stub.label());
        if (tx.destination() != null) {
            provenance.put("destination", tx.destination());
        }
        provenance.put("observedPath", tx.path());
        List<String> warnings = StubSupport.bodyWarnings(tx);
        if (!warnings.isEmpty()) {
            provenance.put("warnings", warnings);
        }

        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("name", stub.label());
        // A shape-matched stub must outrank the catch-all for the same endpoint.
        mapping.put("priority", stub.bodyFields().isEmpty() ? 5 : 1);
        mapping.put("request", request);
        mapping.put("response", response);
        mapping.put("metadata", Map.of("traffictape", provenance));
        return mapping;
    }

    private static String uniqueFileName(StubPlan.Stub stub, Set<String> used) {
        String base = baseName(stub);
        String candidate = base + ".json";
        int suffix = 2;
        while (!used.add(candidate)) {
            candidate = base + "-" + suffix++ + ".json";
        }
        return candidate;
    }

    private static String baseName(StubPlan.Stub stub) {
        HttpTransaction tx = stub.transaction();
        String slug = sanitize(stub.route());
        if (slug.isEmpty()) {
            slug = "root";
        }
        int status = tx.response() == null ? 0 : tx.response().status();
        String scenario = sanitize(stub.scenarioId());
        String name = tx.method().toLowerCase(Locale.ROOT) + "-" + slug + "-" + status;
        return scenario.isEmpty() ? name : name + "-" + scenario;
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("(^-|-$)", "")
                .toLowerCase(Locale.ROOT);
    }
}
