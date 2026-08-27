package io.traffictape.fingerprint;

/**
 * Turns concrete paths into templates ({@code /accounts/99} → {@code /accounts/{id}}).
 *
 * <p>A leftover id becomes part of the route key, so one endpoint splits into one scenario
 * per id. Prefer overriding {@link DefaultPathNormalizer#normalizeSegment(String)} so you keep
 * UUID / ULID / int / hex. A lambda here replaces that set entirely.
 */
@FunctionalInterface
public interface PathNormalizer {

    /**
     * Replaces identifier segments of {@code path} with placeholders such as {@code {id}}.
     * Must be stable: the same path always yields the same template.
     */
    String normalize(String path);

    /**
     * Prefer the framework's matched template when it contains {@code {placeholders}}.
     */
    default String preferTemplate(String frameworkTemplate, String path) {
        if (frameworkTemplate != null && !frameworkTemplate.isBlank() && frameworkTemplate.contains("{")) {
            return frameworkTemplate;
        }
        return normalize(path);
    }
}
