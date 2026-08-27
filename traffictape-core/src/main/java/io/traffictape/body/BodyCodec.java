package io.traffictape.body;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.traffictape.model.BodyCapture;
import io.traffictape.redaction.Redactor;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Converts bounded raw bytes into a corpus {@link BodyCapture}. Never emits raw binary.
 */
public final class BodyCodec {

    private final ObjectMapper mapper;
    private final Redactor redactor;
    private final int maxBytes;
    private final boolean captureTextBodies;

    public BodyCodec(ObjectMapper mapper, Redactor redactor, int maxBytes) {
        this(mapper, redactor, maxBytes, true);
    }

    public BodyCodec(ObjectMapper mapper, Redactor redactor, int maxBytes, boolean captureTextBodies) {
        this.mapper = mapper;
        this.redactor = redactor;
        this.maxBytes = maxBytes;
        this.captureTextBodies = captureTextBodies;
    }

    public BodyCapture decode(byte[] bytes, String contentType, boolean truncated, Long declaredSize) {
        long size = declaredSize != null ? declaredSize : (bytes == null ? 0 : bytes.length);
        if (bytes == null || bytes.length == 0) {
            return BodyCapture.empty();
        }
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (!isCapturable(ct)) {
            return BodyCapture.omitted(size);
        }
        byte[] slice = bytes;
        boolean over = truncated || slice.length > maxBytes;
        if (slice.length > maxBytes) {
            slice = java.util.Arrays.copyOf(slice, maxBytes);
            over = true;
        }
        if (isJson(ct)) {
            try {
                JsonNode node = mapper.readTree(slice);
                JsonNode redacted = redactor.json(node);
                return BodyCapture.json(redacted, over, size, slice.length);
            } catch (Exception e) {
                // Unparseable JSON (usually truncated) cannot be field-redacted, and invalid JSON
                // is useless as a mock body. Omit rather than store it raw.
                return BodyCapture.omitted(size);
            }
        }
        if (isText(ct)) {
            if (!captureTextBodies) {
                return BodyCapture.omitted(size);
            }
            String text = redactor.text(new String(slice, StandardCharsets.UTF_8), ct);
            return BodyCapture.text(text, over, size, slice.length);
        }
        return BodyCapture.omitted(size);
    }

    public static boolean isCapturable(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return true;
        }
        String ct = contentType.toLowerCase(Locale.ROOT);
        if (ct.startsWith("multipart/")
                || ct.contains("octet-stream")
                || ct.startsWith("image/")
                || ct.startsWith("audio/")
                || ct.startsWith("video/")
                || ct.startsWith("application/pdf")
                || ct.startsWith("application/zip")
                || ct.startsWith("application/gzip")) {
            return false;
        }
        return true;
    }

    public static boolean isJson(String contentType) {
        if (contentType == null) {
            return false;
        }
        String ct = contentType.toLowerCase(Locale.ROOT);
        return ct.contains("json");
    }

    public static boolean isText(String contentType) {
        if (contentType == null) {
            return false;
        }
        String ct = contentType.toLowerCase(Locale.ROOT);
        return ct.startsWith("text/") || ct.contains("xml") || ct.contains("javascript") || ct.contains("urlencoded");
    }
}
