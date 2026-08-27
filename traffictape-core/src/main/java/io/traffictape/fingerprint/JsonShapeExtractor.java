package io.traffictape.fingerprint;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Structural JSON shape only: values become types so fingerprints never contain IDs or secrets.
 */
public final class JsonShapeExtractor {

    public static final String NONE = "none";
    private static final int DEFAULT_MAX_DEPTH = 6;
    private static final int DEFAULT_MAX_KEYS = 40;

    private final ObjectMapper mapper;
    private final int maxDepth;
    private final int maxKeys;

    public JsonShapeExtractor(ObjectMapper mapper) {
        this(mapper, DEFAULT_MAX_DEPTH, DEFAULT_MAX_KEYS);
    }

    public JsonShapeExtractor(ObjectMapper mapper, int maxDepth, int maxKeys) {
        this.mapper = mapper;
        this.maxDepth = maxDepth;
        this.maxKeys = maxKeys;
    }

    public String extract(byte[] json) {
        return extract(json, null);
    }

    public String extract(byte[] json, String contentType) {
        if (json == null || json.length == 0) {
            return NONE;
        }
        if (contentType != null && !contentType.isBlank() && !io.traffictape.body.BodyCodec.isJson(contentType)) {
            return NONE;
        }
        try {
            JsonNode node = mapper.readTree(json);
            StringBuilder sb = new StringBuilder();
            write(node, sb, 0);
            return sb.toString();
        } catch (Exception ignored) {
            return "unparsed";
        }
    }

    public String extract(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return NONE;
        }
        StringBuilder sb = new StringBuilder();
        write(node, sb, 0);
        return sb.toString();
    }

    private void write(JsonNode node, StringBuilder sb, int depth) {
        if (depth >= maxDepth) {
            sb.append("truncated");
            return;
        }
        if (node == null || node.isNull()) {
            sb.append("null");
            return;
        }
        if (node.isObject()) {
            sb.append('{');
            TreeMap<String, JsonNode> ordered = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            int count = 0;
            while (fields.hasNext() && count < maxKeys) {
                Map.Entry<String, JsonNode> e = fields.next();
                ordered.put(e.getKey(), e.getValue());
                count++;
            }
            boolean first = true;
            for (Map.Entry<String, JsonNode> e : ordered.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(e.getKey()).append(':');
                write(e.getValue(), sb, depth + 1);
            }
            if (node.size() > maxKeys) {
                if (!first) {
                    sb.append(',');
                }
                sb.append("…");
            }
            sb.append('}');
            return;
        }
        if (node.isArray()) {
            sb.append('[');
            if (!node.isEmpty()) {
                write(node.get(0), sb, depth + 1);
            }
            sb.append(']');
            return;
        }
        if (node.isTextual()) {
            sb.append("string");
        } else if (node.isIntegralNumber()) {
            sb.append("number");
        } else if (node.isFloatingPointNumber()) {
            sb.append("number");
        } else if (node.isBoolean()) {
            sb.append("boolean");
        } else {
            sb.append("value");
        }
    }
}
