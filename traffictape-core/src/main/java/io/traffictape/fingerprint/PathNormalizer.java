package io.traffictape.fingerprint;

import java.util.regex.Pattern;

/**
 * Turns concrete paths into templates without using literal IDs.
 * Prefer a framework-supplied route template when the adapter has one.
 */
public final class PathNormalizer {

    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern ULID = Pattern.compile("[0-9A-HJKMNP-TV-Z]{26}");
    private static final Pattern NUMERIC = Pattern.compile("^[0-9]{1,19}$");
    private static final Pattern HEX = Pattern.compile("^[0-9a-fA-F]{16,64}$");

    public String normalize(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        String p = path;
        int q = p.indexOf('?');
        if (q >= 0) {
            p = p.substring(0, q);
        }
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        String[] parts = p.split("/");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }
            out.append('/').append(normalizeSegment(part));
        }
        return out.isEmpty() ? "/" : out.toString();
    }

    public String preferTemplate(String springTemplate, String path) {
        if (springTemplate != null && !springTemplate.isBlank() && springTemplate.contains("{")) {
            return springTemplate;
        }
        return normalize(path);
    }

    private static String normalizeSegment(String segment) {
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
