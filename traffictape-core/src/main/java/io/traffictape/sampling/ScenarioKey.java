package io.traffictape.sampling;

/**
 * Scenario identity used by the sampler. Not just an endpoint: Claude needs to
 * tell apart request-shape and response variants of the same route.
 *
 * <pre>
 *   GET /accounts/{id} + none + 200
 *   GET /accounts/{id} + none + 200:empty
 *   GET /accounts/{id} + none + 404
 *   PATCH /assets/{id} + {status:string} + 200
 *   PATCH /assets/{id} + {owner:string} + 200
 * </pre>
 *
 * Status-class quotas are not hard-wired. Distinct response characteristics
 * are distinct keys, so a rare 404 is not drowned by a 200 flood.
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
