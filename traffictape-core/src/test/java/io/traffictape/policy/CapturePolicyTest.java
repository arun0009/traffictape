package io.traffictape.policy;

import org.junit.jupiter.api.Test;

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
}
