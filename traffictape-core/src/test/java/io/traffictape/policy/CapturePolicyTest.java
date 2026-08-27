package io.traffictape.policy;

import io.traffictape.capture.ObservedExchange;
import io.traffictape.model.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CapturePolicyTest {

    private final CapturePolicy policy = CapturePolicy.safeDefaults();

    @Test
    void excludesHealthAndActuator() {
        assertThat(policy.acceptsRoute("/health")).isFalse();
        assertThat(policy.acceptsRoute("/actuator/metrics")).isFalse();
        assertThat(policy.acceptsRoute("/orders")).isTrue();
    }

    @Test
    void includesOnlyConfiguredMethods() {
        assertThat(policy.acceptsMethod("GET")).isTrue();
        assertThat(policy.acceptsMethod("POST")).isTrue();
        assertThat(policy.acceptsMethod("HEAD")).isFalse();
        assertThat(policy.acceptsMethod("OPTIONS")).isFalse();
    }

    @Test
    void omitsMultipart() {
        assertThat(policy.acceptsContentType("multipart/form-data")).isFalse();
        assertThat(policy.acceptsContentType("application/json")).isTrue();
    }

    @Test
    void excludesOctetStreamAndMultipartThroughAccepts() {
        ObservedExchange multipart = ObservedExchange.builder()
                .direction(Direction.INBOUND)
                .method("POST")
                .path("/uploads")
                .requestContentType("multipart/form-data; boundary=x")
                .status(200)
                .build();
        ObservedExchange binary = ObservedExchange.builder()
                .direction(Direction.INBOUND)
                .method("GET")
                .path("/files/1")
                .responseContentType("application/octet-stream")
                .status(200)
                .build();
        ObservedExchange json = ObservedExchange.builder()
                .direction(Direction.INBOUND)
                .method("GET")
                .path("/files/1")
                .responseContentType("application/json")
                .status(200)
                .build();
        assertThat(policy.accepts(multipart)).isFalse();
        assertThat(policy.accepts(binary)).isFalse();
        assertThat(policy.accepts(json)).isTrue();
    }

    @Test
    void excludesConfiguredDestinations() {
        CapturePolicy p = CapturePolicy.builder()
                .includeMethods(List.of("GET"))
                .excludeDestinations(List.of("payments.internal"))
                .build();
        ObservedExchange payments = ObservedExchange.builder()
                .direction(Direction.OUTBOUND)
                .method("GET")
                .path("/charge")
                .destination("payments.internal")
                .status(200)
                .build();
        ObservedExchange inventory = ObservedExchange.builder()
                .direction(Direction.OUTBOUND)
                .method("GET")
                .path("/sku")
                .destination("inventory")
                .status(200)
                .build();
        assertThat(p.accepts(payments)).isFalse();
        assertThat(p.accepts(inventory)).isTrue();
    }

    @Test
    void acceptsEverythingWhenNoRequestHeadersAreExcluded() {
        assertThat(policy.acceptsRequestHeaders(Map.of("X-Smoke-Test", List.of("true")))).isTrue();
    }

    @Test
    void excludesOnHeaderPresenceRegardlessOfValue() {
        CapturePolicy p = withExcludedHeaders(Map.of("x-smoke-test", List.of("*")));

        assertThat(p.acceptsRequestHeaders(Map.of("X-Smoke-Test", List.of("true")))).isFalse();
        assertThat(p.acceptsRequestHeaders(Map.of("X-Smoke-Test", List.of("anything")))).isFalse();
        assertThat(p.acceptsRequestHeaders(Map.of("X-Other", List.of("true")))).isTrue();
    }

    @Test
    void anEmptyPatternListMeansPresence() {
        CapturePolicy p = withExcludedHeaders(Map.of("x-smoke-test", List.of()));

        assertThat(p.acceptsRequestHeaders(Map.of("X-Smoke-Test", List.of("true")))).isFalse();
    }

    @Test
    void matchesHeaderNamesCaseInsensitivelyAndValuesByGlob() {
        CapturePolicy p = withExcludedHeaders(Map.of("user-agent", List.of("*synthetic-monitor*", "kube-probe/*")));

        assertThat(p.acceptsRequestHeaders(Map.of("USER-AGENT", List.of("synthetic-monitor/2.0")))).isFalse();
        assertThat(p.acceptsRequestHeaders(Map.of("User-Agent", List.of("SYNTHETIC-MONITOR/2.0")))).isFalse();
        assertThat(p.acceptsRequestHeaders(Map.of("User-Agent", List.of("kube-probe/1.28")))).isFalse();
        assertThat(p.acceptsRequestHeaders(Map.of("User-Agent", List.of("Mozilla/5.0")))).isTrue();
    }

    @Test
    void excludesWhenAnyValueOfARepeatedHeaderMatches() {
        CapturePolicy p = withExcludedHeaders(Map.of("x-tags", List.of("synthetic")));

        assertThat(p.acceptsRequestHeaders(Map.of("X-Tags", List.of("real", "synthetic")))).isFalse();
        assertThat(p.acceptsRequestHeaders(Map.of("X-Tags", List.of("real", "live")))).isTrue();
    }

    @Test
    void aValuePatternDoesNotMatchOnPresenceAlone() {
        CapturePolicy p = withExcludedHeaders(Map.of("user-agent", List.of("kube-probe/*")));

        assertThat(p.acceptsRequestHeaders(Map.of("User-Agent", List.of()))).isTrue();
    }

    @Test
    void requestHeaderExclusionAppliesThroughAccepts() {
        CapturePolicy p = withExcludedHeaders(Map.of("x-smoke-test", List.of("*")));

        ObservedExchange smoke = ObservedExchange.builder()
                .direction(Direction.INBOUND)
                .method("GET")
                .path("/orders")
                .requestHeaders(Map.of("X-Smoke-Test", List.of("true")))
                .status(200)
                .build();
        ObservedExchange real = ObservedExchange.builder()
                .direction(Direction.INBOUND)
                .method("GET")
                .path("/orders")
                .status(200)
                .build();

        assertThat(p.accepts(smoke)).isFalse();
        assertThat(p.accepts(real)).isTrue();
    }

    private static CapturePolicy withExcludedHeaders(Map<String, List<String>> excluded) {
        return CapturePolicy.builder()
                .includeMethods(List.of("GET", "POST"))
                .excludeRequestHeaders(excluded)
                .build();
    }
}
