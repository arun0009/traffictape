package io.traffictape.model;

/**
 * Two fingerprint layers so an AI consumer can distinguish endpoint coverage
 * from scenario coverage.
 *
 * <p>Endpoint: {@code PATCH /assets/{id}}
 *
 * <p>Scenario: {@code PATCH /assets/{id} + {status:string} + 200}
 */
public record FingerprintPair(Fingerprint endpoint, Fingerprint scenario) {
}
