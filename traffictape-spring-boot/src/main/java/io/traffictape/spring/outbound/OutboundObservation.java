package io.traffictape.spring.outbound;

import io.traffictape.capture.CaptureEngine;
import io.traffictape.capture.DestinationHosts;
import io.traffictape.capture.ObservedExchange;
import io.traffictape.correlation.ExchangeContext;
import io.traffictape.model.Direction;
import io.traffictape.spring.TrafficTapeProperties;

import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared mapping from an outbound HTTP hop to {@link CaptureEngine#record}. */
public final class OutboundObservation {

    private OutboundObservation() {
    }

    public static byte[] cap(byte[] body, int maxBytes) {
        return capBody(body, maxBytes).bytes();
    }

    public static CappedBody capBody(byte[] body, int maxBytes) {
        if (body == null || body.length == 0) {
            return CappedBody.empty();
        }
        boolean truncated = body.length > maxBytes;
        byte[] captured = truncated ? Arrays.copyOf(body, maxBytes) : body;
        return new CappedBody(captured, truncated, body.length);
    }

    public static Map<String, List<String>> copyHeaders(Map<String, ? extends List<String>> headers) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (headers != null) {
            headers.forEach((k, v) -> out.put(k, List.copyOf(v)));
        }
        return out;
    }

    public static void record(
            CaptureEngine engine,
            TrafficTapeProperties properties,
            String method,
            URI uri,
            Map<String, List<String>> requestHeaders,
            Map<String, List<String>> responseHeaders,
            String requestContentType,
            String responseContentType,
            byte[] requestBody,
            boolean requestTruncated,
            Long requestDeclaredSize,
            byte[] responseBody,
            boolean responseTruncated,
            Long responseDeclaredSize,
            int status,
            long startNanos,
            ExchangeContext ctx,
            Integer sequence) {
        try {
            String path = uri == null || uri.getRawPath() == null ? "/" : uri.getRawPath();
            engine.record(ObservedExchange.builder()
                    .direction(Direction.OUTBOUND)
                    .timestamp(Instant.now())
                    .method(method)
                    .path(path)
                    .destination(properties.destinationName(DestinationHosts.hostPort(uri)))
                    .query(ObservedExchange.parseQuery(uri == null ? null : uri.getRawQuery()))
                    .requestHeaders(requestHeaders)
                    .responseHeaders(responseHeaders)
                    .requestContentType(requestContentType)
                    .responseContentType(responseContentType)
                    .requestBody(requestBody)
                    .requestTruncated(requestTruncated)
                    .requestDeclaredSize(requestDeclaredSize)
                    .responseBody(responseBody)
                    .responseTruncated(responseTruncated)
                    .responseDeclaredSize(responseDeclaredSize)
                    .status(status)
                    .latencyMs((System.nanoTime() - startNanos) / 1_000_000L)
                    .exchangeContext(ctx)
                    .outboundSequence(sequence)
                    .build());
        } catch (Throwable ignored) {
        }
    }

    public record CappedBody(byte[] bytes, boolean truncated, long declaredSize) {
        public static CappedBody empty() {
            return new CappedBody(new byte[0], false, 0);
        }
    }
}
