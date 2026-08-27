package io.traffictape.sampling;

/**
 * Sampler key: route + request shape + response, so {@code PATCH {status}} and
 * {@code PATCH {owner}} do not share a budget, and a rare 404 is not starved by 200s.
 *
 * <pre>
 *   GET /accounts/{id} + none + 200
 *   GET /accounts/{id} + none + 404
 *   PATCH /assets/{id} + {status:string} + 200
 *   PATCH /assets/{id} + {owner:string} + 200
 * </pre>
 */
public record ScenarioKey(
        String endpointFingerprint,
        String requestShape,
        String responseCharacteristic
) {
    public static String responseCharacteristic(int status, boolean emptyBody) {
        return emptyBody ? status + ":empty" : Integer.toString(status);
    }
}
