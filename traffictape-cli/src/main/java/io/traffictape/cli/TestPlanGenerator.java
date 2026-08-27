package io.traffictape.cli;

import io.traffictape.model.BodyCapture;
import io.traffictape.model.HttpTransaction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Emits one replayable case per inbound scenario, each naming the outbound stubs it needs.
 *
 * <p>This is the part a recording proxy cannot produce: the corpus knows which outbound calls a
 * given inbound request caused, so a case and its mocks stay together.
 */
final class TestPlanGenerator {

    Map<String, Object> generate(Corpus corpus) {
        List<Map<String, Object>> cases = new ArrayList<>();
        corpus.inboundScenarios().forEach((scenarioId, tx) -> {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("method", tx.method());
            request.put("route", tx.route());
            request.put("observedPath", tx.path());
            if (tx.query() != null && !tx.query().isEmpty()) {
                request.put("query", tx.query());
            }
            if (tx.request() != null && tx.request().contentType() != null) {
                request.put("contentType", tx.request().contentType());
            }
            BodyCapture requestBody = tx.request() == null ? null : tx.request().body();
            if (StubSupport.hasReplayableBody(requestBody)) {
                request.put("body", requestBody.body());
            } else if (requestBody != null) {
                request.put("bodyUnavailable", requestBody.encoding().name());
            }

            Map<String, Object> expect = new LinkedHashMap<>();
            expect.put("status", tx.response() == null ? 0 : tx.response().status());
            BodyCapture responseBody = tx.response() == null ? null : tx.response().body();
            if (StubSupport.hasReplayableBody(responseBody)) {
                expect.put("body", responseBody.body());
            } else if (responseBody != null) {
                expect.put("bodyUnavailable", responseBody.encoding().name());
            }

            List<Map<String, Object>> dependsOn = new ArrayList<>();
            for (String outboundScenario : corpus.dependenciesOf(scenarioId)) {
                HttpTransaction outbound = corpus.outboundScenarios().get(outboundScenario);
                Map<String, Object> dependency = new LinkedHashMap<>();
                dependency.put("scenario", outboundScenario);
                if (outbound != null) {
                    dependency.put("label", labelOf(outbound));
                    if (outbound.destination() != null) {
                        dependency.put("destination", outbound.destination());
                    }
                }
                dependsOn.add(dependency);
            }

            Map<String, Object> testCase = new LinkedHashMap<>();
            testCase.put("scenario", scenarioId);
            testCase.put("label", labelOf(tx));
            testCase.put("request", request);
            testCase.put("expect", expect);
            testCase.put("observedLatencyMs", tx.latencyMs());
            testCase.put("dependsOn", dependsOn);
            cases.add(testCase);
        });

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("events", corpus.totalEvents());
        counts.put("inboundScenarios", corpus.inboundScenarios().size());
        counts.put("outboundScenarios", corpus.outboundScenarios().size());

        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("schemaVersion", "1");
        plan.put("generatedAt", Instant.now().toString());
        plan.put("corpus", counts);
        plan.put("cases", cases);
        return plan;
    }

    private static String labelOf(HttpTransaction tx) {
        if (tx.fingerprints() != null && tx.fingerprints().scenario() != null
                && tx.fingerprints().scenario().label() != null) {
            return tx.fingerprints().scenario().label();
        }
        return tx.method() + " " + tx.route();
    }
}
