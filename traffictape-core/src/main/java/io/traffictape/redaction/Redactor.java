package io.traffictape.redaction;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * Removes sensitive data before it enters the capture queue.
 *
 * <p>Replace this when a name-based denylist is not enough — value-shaped detection (card numbers,
 * national IDs, emails in free text), an existing in-house classifier, or per-tenant rules. This is
 * the seam a security review will ask about, so it is deliberately substitutable:
 *
 * <pre>{@code
 * @Bean
 * Redactor ourRedactor(CapturePolicy policy) {
 *     return new DefaultRedactor(policy) {
 *         @Override
 *         public String text(String body, String contentType) {
 *             return CARD_NUMBER.matcher(super.text(body, contentType)).replaceAll(REDACTED);
 *         }
 *     };
 * }
 * }</pre>
 *
 * <p>Implementations run on the application request thread. They must be fast and must not throw;
 * the caller drops the example rather than failing the request.
 */
public interface Redactor {

    String REDACTED = "[REDACTED]";

    /** Returns headers with denylisted names replaced by {@link #REDACTED}. */
    Map<String, List<String>> headers(Map<String, List<String>> headers);

    /** Returns a copy of {@code node} with denylisted fields redacted and dropped fields removed. */
    JsonNode json(JsonNode node);

    /**
     * Redacts a non-JSON text body. Formats with no field structure to key off cannot be redacted
     * field-wise; return the body unchanged and let {@code capture.text-bodies} omit it, or
     * implement value-shaped detection here.
     */
    String text(String body, String contentType);
}
