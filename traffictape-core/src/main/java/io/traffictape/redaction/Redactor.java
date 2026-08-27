package io.traffictape.redaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.traffictape.policy.CapturePolicy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redacts headers, JSON, and structured text bodies before data enters the capture queue.
 * Failures must be handled by the caller (drop the example, never fail the app).
 */
public final class Redactor {

    public static final String REDACTED = "[REDACTED]";
    private final CapturePolicy policy;

    /**
     * Both patterns are null when no field denylist is configured. Character classes are used
     * instead of reluctant wildcards so matching stays linear on the request thread; the cost is
     * that a denylisted XML element containing nested elements or CDATA is not matched.
     */
    private final Pattern xmlElement;
    private final Pattern formField;

    public Redactor(CapturePolicy policy) {
        this.policy = policy;
        Set<String> fields = policy.excludeJsonFields();
        if (fields.isEmpty()) {
            this.xmlElement = null;
            this.formField = null;
        } else {
            String names = alternation(fields);
            this.xmlElement = Pattern.compile(
                    "<((?:[\\w.-]+:)?(?:" + names + "))([^>]*)>([^<]*)</\\1\\s*>",
                    Pattern.CASE_INSENSITIVE);
            this.formField = Pattern.compile(
                    "(^|&)((?:" + names + ")=)[^&]*",
                    Pattern.CASE_INSENSITIVE);
        }
    }

    private static String alternation(Set<String> names) {
        StringJoiner joiner = new StringJoiner("|");
        for (String name : names) {
            joiner.add(Pattern.quote(name));
        }
        return joiner.toString();
    }

    public Map<String, List<String>> headers(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        headers.forEach((name, values) -> {
            if (!policy.captureHeader(name)) {
                out.put(name, List.of(REDACTED));
                return;
            }
            out.put(name, values == null ? List.of() : List.copyOf(values));
        });
        return out;
    }

    public JsonNode json(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return node;
        }
        return redactNode(node.deepCopy());
    }

    /**
     * Applies the field denylist to XML and form-urlencoded bodies. Other text formats have no
     * field structure to key off, so they are returned unchanged — omit them with
     * {@code traffictape.capture.text-bodies: false} if that is not acceptable.
     */
    public String text(String body, String contentType) {
        if (body == null || body.isEmpty()) {
            return body;
        }
        String ct = contentType == null ? "" : contentType.toLowerCase(java.util.Locale.ROOT);
        if (formField != null && ct.contains("urlencoded")) {
            return formField.matcher(body)
                    .replaceAll("$1$2" + Matcher.quoteReplacement(REDACTED));
        }
        if (xmlElement != null && ct.contains("xml")) {
            return xmlElement.matcher(body)
                    .replaceAll("<$1$2>" + Matcher.quoteReplacement(REDACTED) + "</$1>");
        }
        return body;
    }

    private JsonNode redactNode(JsonNode node) {
        if (node instanceof ObjectNode object) {
            List<String> names = new ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                if (!policy.keepJsonField(name)) {
                    object.remove(name);
                    continue;
                }
                if (policy.redactJsonField(name)) {
                    object.put(name, REDACTED);
                } else {
                    JsonNode child = object.get(name);
                    object.set(name, redactNode(child));
                }
            }
            return object;
        }
        if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                array.set(i, redactNode(array.get(i)));
            }
        }
        return node;
    }
}
