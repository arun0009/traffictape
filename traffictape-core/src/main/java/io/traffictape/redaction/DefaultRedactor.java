package io.traffictape.redaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.traffictape.policy.CapturePolicy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Name denylist for headers, JSON, XML (elements and attributes), and form fields.
 * A secret in a field you did not list is stored as-is — override {@link #text} or {@link #json}.
 */
public class DefaultRedactor implements Redactor {

    private final CapturePolicy policy;

    /** All three are null when no field denylist is configured. */
    private final Pattern xmlElement;
    private final Pattern xmlAttribute;
    private final Pattern formField;

    public DefaultRedactor(CapturePolicy policy) {
        this.policy = policy;
        Set<String> fields = policy.excludeJsonFields();
        if (fields.isEmpty()) {
            this.xmlElement = null;
            this.xmlAttribute = null;
            this.formField = null;
        } else {
            String names = alternation(fields);
            // DOTALL with a reluctant body so nested elements and CDATA inside a denylisted
            // element are replaced rather than skipped. Bodies are size-capped before they reach
            // here, which bounds the scan.
            this.xmlElement = Pattern.compile(
                    "<((?:[\\w.-]+:)?(?:" + names + "))((?:\\s[^>]*)?)>(.*?)</\\1\\s*>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            // Legacy XML often carries values as attributes, where an element-shaped pattern
            // never matches.
            this.xmlAttribute = Pattern.compile(
                    "\\b((?:" + names + "))(\\s*=\\s*)(\"[^\"]*\"|'[^']*')",
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

    @Override
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

    @Override
    public JsonNode json(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return node;
        }
        return redactNode(node.deepCopy());
    }

    @Override
    public String text(String body, String contentType) {
        if (body == null || body.isEmpty()) {
            return body;
        }
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (formField != null && ct.contains("urlencoded")) {
            return formField.matcher(body)
                    .replaceAll("$1$2" + Matcher.quoteReplacement(REDACTED));
        }
        if (xmlElement != null && ct.contains("xml")) {
            // Elements first: it collapses child content, so the attribute pass then only sees
            // attributes that survived on the remaining tags.
            String out = xmlElement.matcher(body)
                    .replaceAll("<$1$2>" + Matcher.quoteReplacement(REDACTED) + "</$1>");
            return xmlAttribute.matcher(out)
                    .replaceAll("$1$2\"" + Matcher.quoteReplacement(REDACTED) + "\"");
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
                    object.set(name, redactNode(object.get(name)));
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
