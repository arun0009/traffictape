package io.traffictape.fingerprint;

import io.traffictape.model.Direction;
import io.traffictape.model.Fingerprint;
import io.traffictape.model.FingerprintPair;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Pluggable fingerprinting. Default never includes literal values.
 *
 * <p>Endpoint identity: direction, method, normalized route, query parameter names.
 * Scenario identity: endpoint + request body shape + response characteristic.
 *
 * <p>To replace: implement this and expose a {@code @Bean Fingerprinter}. The default
 * backs off via {@code @ConditionalOnMissingBean}.
 */
public interface Fingerprinter {

    Fingerprint endpoint(
            Direction direction,
            String method,
            String route,
            Map<String, List<String>> query);

    Fingerprint scenario(Fingerprint endpoint, String requestShape, String responseCharacteristic);

    default FingerprintPair pair(
            Direction direction,
            String method,
            String route,
            Map<String, List<String>> query,
            String requestShape,
            String responseCharacteristic) {
        Fingerprint endpoint = endpoint(direction, method, route, query);
        return new FingerprintPair(endpoint, scenario(endpoint, requestShape, responseCharacteristic));
    }

    static String queryShape(Map<String, List<String>> query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        return new TreeSet<>(query.keySet()).stream()
                .map(String::toLowerCase)
                .collect(Collectors.joining(","));
    }

    static String hash(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes).substring(0, 16);
        } catch (Exception e) {
            return Integer.toHexString(canonical.hashCode());
        }
    }
}
