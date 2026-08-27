package io.traffictape.fingerprint;

import java.util.regex.Pattern;

/**
 * Recognises UUIDs, ULIDs, integers, and long hex strings. Slugs, tenant codes, and opaque tokens
 * are treated as literal segments, so most adopters need to add at least one shape.
 *
 * <p>Override {@link #normalizeSegment(String)} to add shapes while keeping the built-in ones,
 * rather than reimplementing the set:
 *
 * <pre>{@code
 * @Bean
 * PathNormalizer accountAwareNormalizer() {
 *     return new DefaultPathNormalizer() {
 *         @Override
 *         protected String normalizeSegment(String segment) {
 *             return segment.startsWith("acct_") ? "{account}" : super.normalizeSegment(segment);
 *         }
 *     };
 * }
 * }</pre>
 */
public class DefaultPathNormalizer implements PathNormalizer {

    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern ULID = Pattern.compile("[0-9A-HJKMNP-TV-Z]{26}");
    private static final Pattern NUMERIC = Pattern.compile("^[0-9]{1,19}$");
    private static final Pattern HEX = Pattern.compile("^[0-9a-fA-F]{16,64}$");

    @Override
    public String normalize(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        String p = path;
        int q = p.indexOf('?');
        if (q >= 0) {
            p = p.substring(0, q);
        }
        StringBuilder out = new StringBuilder();
        for (String part : p.split("/")) {
            if (!part.isEmpty()) {
                out.append('/').append(normalizeSegment(part));
            }
        }
        return out.isEmpty() ? "/" : out.toString();
    }

    /**
     * Maps one path segment to a placeholder, or returns it unchanged when it looks like a literal.
     * Called for every non-empty segment; must be stable for a given input.
     */
    protected String normalizeSegment(String segment) {
        if (UUID.matcher(segment).matches()) {
            return "{uuid}";
        }
        if (ULID.matcher(segment).matches()) {
            return "{ulid}";
        }
        if (NUMERIC.matcher(segment).matches()) {
            return "{id}";
        }
        if (HEX.matcher(segment).matches()) {
            return "{hash}";
        }
        return segment;
    }
}
