package io.traffictape.redaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.traffictape.policy.CapturePolicy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Redacts headers and JSON before data enters the capture queue.
 * Failures must be handled by the caller (drop the example, never fail the app).
 */
public final class Redactor {

    public static final String REDACTED = "[REDACTED]";
    private final CapturePolicy policy;

    public Redactor(CapturePolicy policy) {
        this.policy = policy;
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
