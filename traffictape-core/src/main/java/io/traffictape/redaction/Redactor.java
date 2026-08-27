package io.traffictape.redaction;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * Redacts secrets before an event is queued. Default is a name denylist; override for
 * value matching (card numbers in free text, and so on):
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
 * Runs on the request thread. Do not throw; a failure drops the example, not the request.
 */
public interface Redactor {

    String REDACTED = "[REDACTED]";

    /** Returns headers with denylisted names replaced by {@link #REDACTED}. */
    Map<String, List<String>> headers(Map<String, List<String>> headers);

    /** Returns a copy of {@code node} with denylisted fields redacted and dropped fields removed. */
    JsonNode json(JsonNode node);

    /**
     * Redacts XML, form bodies, and other text. Return the body unchanged if there are no
     * field names to key off; set {@code capture.text-bodies: false} to drop those entirely.
     */
    String text(String body, String contentType);
}
