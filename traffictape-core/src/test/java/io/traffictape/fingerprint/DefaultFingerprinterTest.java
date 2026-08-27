package io.traffictape.fingerprint;

import io.traffictape.model.Direction;
import io.traffictape.model.Fingerprint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultFingerprinterTest {

    private final DefaultFingerprinter fingerprinter = new DefaultFingerprinter();

    @Test
    void endpointIgnoresPathIds() {
        Fingerprint a = fingerprinter.endpoint(Direction.INBOUND, "GET", "/accounts/{id}", Map.of());
        Fingerprint b = fingerprinter.endpoint(Direction.INBOUND, "GET", "/accounts/{id}", Map.of());
        assertThat(a.id()).isEqualTo(b.id());
        assertThat(a.label()).contains("GET /accounts/{id}");
    }

    @Test
    void queryNamesArePartOfEndpointNotValues() {
        Fingerprint a = fingerprinter.endpoint(Direction.INBOUND, "GET", "/search", Map.of("q", List.of("one")));
        Fingerprint b = fingerprinter.endpoint(Direction.INBOUND, "GET", "/search", Map.of("q", List.of("two")));
        assertThat(a.id()).isEqualTo(b.id());
    }

    @Test
    void scenarioSplitsRequestShapes() {
        Fingerprint endpoint = fingerprinter.endpoint(Direction.INBOUND, "PATCH", "/assets/{id}", Map.of());
        Fingerprint status = fingerprinter.scenario(endpoint, "{status:string}", "200");
        Fingerprint owner = fingerprinter.scenario(endpoint, "{owner:string}", "200");
        assertThat(status.id()).isNotEqualTo(owner.id());
        assertThat(status.id()).isNotEqualTo(endpoint.id());
    }

    @Test
    void responseCharacteristicSplitsScenarios() {
        Fingerprint endpoint = fingerprinter.endpoint(Direction.INBOUND, "GET", "/accounts/{id}", Map.of());
        Fingerprint ok = fingerprinter.scenario(endpoint, "none", "200");
        Fingerprint missing = fingerprinter.scenario(endpoint, "none", "404");
        assertThat(ok.id()).isNotEqualTo(missing.id());
    }
}
