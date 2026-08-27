package io.traffictape.correlation;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-process correlation for one inbound request and the outbound calls it causes.
 * No tracing vendor is required. Adapters copy this onto a ThreadLocal / request
 * attribute / Reactor Context.
 */
public final class ExchangeContext {

    private final String exchangeId;
    private final String traceId;
    private final String spanId;
    private final String correlationId;
    private final AtomicInteger outboundSequence = new AtomicInteger();

    public ExchangeContext(String exchangeId, String traceId, String spanId, String correlationId) {
        this.exchangeId = exchangeId;
        this.traceId = traceId;
        this.spanId = spanId;
        this.correlationId = correlationId;
    }

    public static ExchangeContext open(Map<String, String> headers) {
        ParsedTrace parsed = TraceHeaders.parse(headers);
        return new ExchangeContext(UUID.randomUUID().toString(), parsed.traceId(), parsed.spanId(), parsed.correlationId());
    }

    public String exchangeId() {
        return exchangeId;
    }

    public String traceId() {
        return traceId;
    }

    public String spanId() {
        return spanId;
    }

    public String correlationId() {
        return correlationId;
    }

    public int nextOutboundSequence() {
        return outboundSequence.incrementAndGet();
    }

    public int outboundCount() {
        return outboundSequence.get();
    }

    public record ParsedTrace(String traceId, String spanId, String correlationId) {
    }

    public static final class TraceHeaders {
        private TraceHeaders() {
        }

        public static ParsedTrace parse(Map<String, String> headers) {
            if (headers == null || headers.isEmpty()) {
                return new ParsedTrace(null, null, null);
            }
            String traceparent = header(headers, "traceparent");
            if (traceparent != null) {
                String[] parts = traceparent.trim().split("-");
                if (parts.length >= 3) {
                    return new ParsedTrace(parts[1], parts[2], firstCorrelation(headers));
                }
            }
            String b3 = header(headers, "x-b3-traceid");
            String b3span = header(headers, "x-b3-spanid");
            return new ParsedTrace(b3, b3span, firstCorrelation(headers));
        }

        private static String firstCorrelation(Map<String, String> headers) {
            String c = header(headers, "x-correlation-id");
            if (c != null) {
                return c;
            }
            return header(headers, "x-request-id");
        }

        private static String header(Map<String, String> headers, String name) {
            String direct = headers.get(name);
            if (direct != null && !direct.isBlank()) {
                return direct;
            }
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() != null && e.getKey().toLowerCase(Locale.ROOT).equals(name)) {
                    return e.getValue();
                }
            }
            return null;
        }
    }
}
