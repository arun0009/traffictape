package io.traffictape.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BodyCapture(
        BodyEncoding encoding,
        Object body,
        boolean truncated,
        long sizeBytes,
        long capturedBytes
) {
    public static BodyCapture empty() {
        return new BodyCapture(BodyEncoding.EMPTY, null, false, 0, 0);
    }

    public static BodyCapture omitted(long sizeBytes) {
        return omitted(sizeBytes, false);
    }

    public static BodyCapture omitted(long sizeBytes, boolean truncated) {
        return new BodyCapture(BodyEncoding.OMITTED, null, truncated, sizeBytes, 0);
    }

    public static BodyCapture json(Object body, boolean truncated, long sizeBytes, long capturedBytes) {
        return new BodyCapture(BodyEncoding.JSON, body, truncated, sizeBytes, capturedBytes);
    }

    public static BodyCapture text(String body, boolean truncated, long sizeBytes, long capturedBytes) {
        return new BodyCapture(BodyEncoding.TEXT, body, truncated, sizeBytes, capturedBytes);
    }

    public boolean isEmpty() {
        return encoding == BodyEncoding.EMPTY || sizeBytes == 0;
    }
}
