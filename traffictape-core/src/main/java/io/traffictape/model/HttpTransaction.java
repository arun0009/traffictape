package io.traffictape.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * One HTTP request/response pair. Adapters produce these events; they are not Spring types.
 *
 * <p>Schema version {@code 1}. Field names are stable so offline tools can depend on them.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HttpTransaction(
        String schemaVersion,
        EventType eventType,
        Direction direction,
        Instant timestamp,
        Correlation correlation,
        String destination,
        String method,
        String route,
        String path,
        Map<String, List<String>> query,
        FingerprintPair fingerprints,
        String requestShape,
        String responseCharacteristic,
        long latencyMs,
        HttpRequest request,
        HttpResponse response
) {
    public static final String SCHEMA_VERSION = "1";

    public String endpointFingerprintId() {
        return fingerprints == null || fingerprints.endpoint() == null ? null : fingerprints.endpoint().id();
    }

    public String scenarioFingerprintId() {
        return fingerprints == null || fingerprints.scenario() == null ? null : fingerprints.scenario().id();
    }
}
