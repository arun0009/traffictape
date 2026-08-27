package io.traffictape.fingerprint;

/**
 * Turns concrete paths into templates without using literal IDs.
 *
 * <p>Replace this when your identifiers are not the shapes {@link DefaultPathNormalizer} knows.
 * Getting it wrong is expensive rather than merely imperfect: an identifier that survives
 * normalization becomes part of the endpoint fingerprint, so one endpoint fragments into a
 * scenario per distinct ID and the corpus stops being a summary of behaviour.
 *
 * <p>Usually you want to add a shape rather than replace the set, which means extending
 * {@link DefaultPathNormalizer#normalizeSegment(String)}. Implement this interface directly only
 * when you intend to own normalization entirely — a bare lambda silently drops the built-in UUID,
 * ULID, integer, and hex handling.
 */
@FunctionalInterface
public interface PathNormalizer {

    /**
     * Replaces identifier segments of {@code path} with placeholders such as {@code {id}}.
     * Must be stable: the same path always yields the same template.
     */
    String normalize(String path);

    /**
     * Uses the route template the web framework already matched, falling back to
     * {@link #normalize} when the adapter has none. A framework template is always better than
     * inference, so implementations rarely need to override this.
     */
    default String preferTemplate(String frameworkTemplate, String path) {
        if (frameworkTemplate != null && !frameworkTemplate.isBlank() && frameworkTemplate.contains("{")) {
            return frameworkTemplate;
        }
        return normalize(path);
    }
}
