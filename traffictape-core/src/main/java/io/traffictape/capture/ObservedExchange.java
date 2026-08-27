package io.traffictape.capture;

import io.traffictape.correlation.ExchangeContext;
import io.traffictape.model.Direction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What an adapter submits to {@link CaptureEngine#record}. Framework-agnostic.
 */
public record ObservedExchange(
        Direction direction,
        Instant timestamp,
        String method,
        String route,
        String path,
        String destination,
        Map<String, List<String>> query,
        Map<String, List<String>> requestHeaders,
        Map<String, List<String>> responseHeaders,
        String requestContentType,
        String responseContentType,
        byte[] requestBody,
        byte[] responseBody,
        boolean requestTruncated,
        boolean responseTruncated,
        Long requestDeclaredSize,
        Long responseDeclaredSize,
        int status,
        long latencyMs,
        ExchangeContext exchangeContext,
        Integer outboundSequence
) {

    public ObservedExchange {
        timestamp = timestamp == null ? Instant.now() : timestamp;
        query = query == null ? Map.of() : query;
        requestHeaders = requestHeaders == null ? Map.of() : requestHeaders;
        responseHeaders = responseHeaders == null ? Map.of() : responseHeaders;
        requestBody = requestBody == null ? new byte[0] : requestBody;
        responseBody = responseBody == null ? new byte[0] : responseBody;
    }

    public static Builder builder() {
        return new Builder();
    }

    public long requestSize() {
        return requestDeclaredSize != null ? requestDeclaredSize : requestBody.length;
    }

    public long responseSize() {
        return responseDeclaredSize != null ? responseDeclaredSize : responseBody.length;
    }

    public static Map<String, List<String>> parseQuery(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            String name = eq < 0 ? part : part.substring(0, eq);
            String value = eq < 0 ? "" : part.substring(eq + 1);
            out.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        }
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
            this.query = query;
            return this;
        }

        public Builder requestHeaders(Map<String, List<String>> requestHeaders) {
            this.requestHeaders = requestHeaders;
            return this;
        }

        public Builder responseHeaders(Map<String, List<String>> responseHeaders) {
            this.responseHeaders = responseHeaders;
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
            this.requestBody = requestBody;
            return this;
        }

        public Builder responseBody(byte[] responseBody) {
            this.responseBody = responseBody;
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
            return new ObservedExchange(
                    direction,
                    timestamp,
                    method,
                    route,
                    path,
                    destination,
                    query,
                    requestHeaders,
                    responseHeaders,
                    requestContentType,
                    responseContentType,
                    requestBody,
                    responseBody,
                    requestTruncated,
                    responseTruncated,
                    requestDeclaredSize,
                    responseDeclaredSize,
                    status,
                    latencyMs,
                    exchangeContext,
                    outboundSequence);
        }
    }
}
