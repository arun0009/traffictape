package io.traffictape.capture;

import io.traffictape.correlation.ExchangeContext;
import io.traffictape.model.Direction;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What an adapter submits to {@link CaptureEngine#record}. Framework-agnostic.
 *
 * <p>To add a new runtime (Quarkus, servlet, a test), build one of these and call {@code record}.
 * Do not depend on Spring types here.
 */
public final class ObservedExchange {

    private final Direction direction;
    private final Instant timestamp;
    private final String method;
    private final String route;
    private final String path;
    private final String destination;
    private final Map<String, List<String>> query;
    private final Map<String, List<String>> requestHeaders;
    private final Map<String, List<String>> responseHeaders;
    private final String requestContentType;
    private final String responseContentType;
    private final byte[] requestBody;
    private final byte[] responseBody;
    private final boolean requestTruncated;
    private final boolean responseTruncated;
    private final Long requestDeclaredSize;
    private final Long responseDeclaredSize;
    private final int status;
    private final long latencyMs;
    private final ExchangeContext exchangeContext;
    private final Integer outboundSequence;

    private ObservedExchange(Builder b) {
        this.direction = b.direction;
        this.timestamp = b.timestamp == null ? Instant.now() : b.timestamp;
        this.method = b.method;
        this.route = b.route;
        this.path = b.path;
        this.destination = b.destination;
        this.query = b.query == null ? Map.of() : b.query;
        this.requestHeaders = b.requestHeaders == null ? Map.of() : b.requestHeaders;
        this.responseHeaders = b.responseHeaders == null ? Map.of() : b.responseHeaders;
        this.requestContentType = b.requestContentType;
        this.responseContentType = b.responseContentType;
        this.requestBody = b.requestBody == null ? new byte[0] : b.requestBody;
        this.responseBody = b.responseBody == null ? new byte[0] : b.responseBody;
        this.requestTruncated = b.requestTruncated;
        this.responseTruncated = b.responseTruncated;
        this.requestDeclaredSize = b.requestDeclaredSize;
        this.responseDeclaredSize = b.responseDeclaredSize;
        this.status = b.status;
        this.latencyMs = b.latencyMs;
        this.exchangeContext = b.exchangeContext;
        this.outboundSequence = b.outboundSequence;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Direction direction() {
        return direction;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public String method() {
        return method;
    }

    public String route() {
        return route;
    }

    public String path() {
        return path;
    }

    public String destination() {
        return destination;
    }

    public Map<String, List<String>> query() {
        return query;
    }

    public Map<String, List<String>> requestHeaders() {
        return requestHeaders;
    }

    public Map<String, List<String>> responseHeaders() {
        return responseHeaders;
    }

    public String requestContentType() {
        return requestContentType;
    }

    public String responseContentType() {
        return responseContentType;
    }

    public byte[] requestBody() {
        return requestBody;
    }

    public byte[] responseBody() {
        return responseBody;
    }

    public boolean requestTruncated() {
        return requestTruncated;
    }

    public boolean responseTruncated() {
        return responseTruncated;
    }

    public Long requestDeclaredSize() {
        return requestDeclaredSize;
    }

    public Long responseDeclaredSize() {
        return responseDeclaredSize;
    }

    public int status() {
        return status;
    }

    public long latencyMs() {
        return latencyMs;
    }

    public ExchangeContext exchangeContext() {
        return exchangeContext;
    }

    public Integer outboundSequence() {
        return outboundSequence;
    }

    public long requestSize() {
        if (requestDeclaredSize != null) {
            return requestDeclaredSize;
        }
        return requestBody.length;
    }

    public long responseSize() {
        if (responseDeclaredSize != null) {
            return responseDeclaredSize;
        }
        return responseBody.length;
    }

    public static Map<String, List<String>> singleHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        headers.forEach((k, v) -> out.put(k, v == null ? List.of() : List.of(v)));
        return out;
    }

    public static final class Builder {
        private Direction direction = Direction.INBOUND;
        private Instant timestamp;
        private String method;
        private String route;
        private String path;
        private String destination;
        private Map<String, List<String>> query = Map.of();
        private Map<String, List<String>> requestHeaders = Map.of();
        private Map<String, List<String>> responseHeaders = Map.of();
        private String requestContentType;
        private String responseContentType;
        private byte[] requestBody = new byte[0];
        private byte[] responseBody = new byte[0];
        private boolean requestTruncated;
        private boolean responseTruncated;
        private Long requestDeclaredSize;
        private Long responseDeclaredSize;
        private int status;
        private long latencyMs;
        private ExchangeContext exchangeContext;
        private Integer outboundSequence;

        public Builder direction(Direction direction) {
            this.direction = direction;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder route(String route) {
            this.route = route;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder destination(String destination) {
            this.destination = destination;
            return this;
        }

        public Builder query(Map<String, List<String>> query) {
            this.query = query == null ? Map.of() : query;
            return this;
        }

        public Builder requestHeaders(Map<String, List<String>> requestHeaders) {
            this.requestHeaders = requestHeaders == null ? Map.of() : requestHeaders;
            return this;
        }

        public Builder responseHeaders(Map<String, List<String>> responseHeaders) {
            this.responseHeaders = responseHeaders == null ? Map.of() : responseHeaders;
            return this;
        }

        public Builder requestContentType(String requestContentType) {
            this.requestContentType = requestContentType;
            return this;
        }

        public Builder responseContentType(String responseContentType) {
            this.responseContentType = responseContentType;
            return this;
        }

        public Builder requestBody(byte[] requestBody) {
            this.requestBody = requestBody == null ? new byte[0] : requestBody;
            return this;
        }

        public Builder responseBody(byte[] responseBody) {
            this.responseBody = responseBody == null ? new byte[0] : responseBody;
            return this;
        }

        public Builder requestTruncated(boolean requestTruncated) {
            this.requestTruncated = requestTruncated;
            return this;
        }

        public Builder responseTruncated(boolean responseTruncated) {
            this.responseTruncated = responseTruncated;
            return this;
        }

        public Builder requestDeclaredSize(Long requestDeclaredSize) {
            this.requestDeclaredSize = requestDeclaredSize;
            return this;
        }

        public Builder responseDeclaredSize(Long responseDeclaredSize) {
            this.responseDeclaredSize = responseDeclaredSize;
            return this;
        }

        public Builder status(int status) {
            this.status = status;
            return this;
        }

        public Builder latencyMs(long latencyMs) {
            this.latencyMs = latencyMs;
            return this;
        }

        public Builder exchangeContext(ExchangeContext exchangeContext) {
            this.exchangeContext = exchangeContext;
            return this;
        }

        public Builder outboundSequence(Integer outboundSequence) {
            this.outboundSequence = outboundSequence;
            return this;
        }

        public ObservedExchange build() {
            return new ObservedExchange(this);
        }
    }
}
