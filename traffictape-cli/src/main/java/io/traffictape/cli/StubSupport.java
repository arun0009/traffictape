package io.traffictape.cli;

import io.traffictape.model.BodyCapture;
import io.traffictape.model.BodyEncoding;
import io.traffictape.model.HttpTransaction;
import io.traffictape.redaction.Redactor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Conversions shared by the WireMock and Mountebank generators. */
final class StubSupport {

    /**
     * Headers a mock server computes itself, or that describe a connection that no longer exists.
     * Replaying them produces responses that hang or decode incorrectly.
     */
    private static final Set<String> DROPPED_HEADERS = Set.of(
            "transfer-encoding", "content-length", "connection", "keep-alive",
            "upgrade", "proxy-authenticate", "proxy-authorization", "te", "trailer", "date");

    private StubSupport() {
    }

    /** True when the route carries {@code {placeholders}} and needs regex matching. */
    static boolean isTemplated(String route) {
        return route != null && route.indexOf('{') >= 0;
    }

    /**
     * Converts a route template into a regex. {@code /assets/{id}} becomes
     * {@code /assets/[^/]+} with every literal character escaped.
     */
    static String routeToRegex(String route) {
        StringBuilder out = new StringBuilder();
        StringBuilder literal = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < route.length(); i++) {
            char c = route.charAt(i);
            if (c == '{') {
                if (depth == 0) {
                    out.append(escape(literal.toString()));
                    literal.setLength(0);
                }
                depth++;
            } else if (c == '}' && depth > 0) {
                depth--;
                if (depth == 0) {
                    out.append("[^/]+");
                }
            } else if (depth == 0) {
                literal.append(c);
            }
        }
        out.append(escape(literal.toString()));
        return out.toString();
    }

    private static String escape(String literal) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < literal.length(); i++) {
            char c = literal.charAt(i);
            if ("\\.[]{}()<>*+-=!?^$|".indexOf(c) >= 0) {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }

    /**
     * Drops hop-by-hop headers and any value redaction replaced, so a placeholder is never served
     * back as if it were a real credential.
     */
    static Map<String, Object> responseHeaders(Map<String, List<String>> headers) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (headers == null) {
            return out;
        }
        headers.forEach((name, values) -> {
            if (name == null || values == null || values.isEmpty()) {
                return;
            }
            if (DROPPED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                return;
            }
            List<String> kept = new ArrayList<>();
            for (String value : values) {
                if (value != null && !Redactor.REDACTED.equals(value)) {
                    kept.add(value);
                }
            }
            if (kept.isEmpty()) {
                return;
            }
            out.put(name, kept.size() == 1 ? kept.get(0) : kept);
        });
        return out;
    }

    /** Top-level field names of a captured JSON object body, used to disambiguate by shape. */
    static List<String> topLevelJsonFields(BodyCapture body) {
        if (body == null || body.encoding() != BodyEncoding.JSON) {
            return List.of();
        }
        if (!(body.body() instanceof Map<?, ?> object)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Object key : object.keySet()) {
            if (key != null) {
                names.add(key.toString());
            }
        }
        return names;
    }

    static boolean hasReplayableBody(BodyCapture body) {
        if (body == null) {
            return false;
        }
        return body.encoding() == BodyEncoding.JSON || body.encoding() == BodyEncoding.TEXT;
    }

    /**
     * Why a stub's response body is not faithful to what was observed. Empty when it is.
     */
    static List<String> bodyWarnings(HttpTransaction tx) {
        List<String> warnings = new ArrayList<>();
        BodyCapture body = tx.response() == null ? null : tx.response().body();
        if (body == null || body.encoding() == BodyEncoding.OMITTED) {
            warnings.add("response body was omitted at capture time; fill it in by hand");
        } else if (body.truncated()) {
            warnings.add("response body was truncated at the byte cap");
        }
        return warnings;
    }

    /** Prefers a 2xx example when several scenarios collapse onto the same request matcher. */
    static boolean preferOver(HttpTransaction candidate, HttpTransaction current) {
        return successful(candidate) && !successful(current);
    }

    private static boolean successful(HttpTransaction tx) {
        int status = tx.response() == null ? 0 : tx.response().status();
        return status >= 200 && status < 300;
    }
}
