package io.traffictape.spring.outbound.jersey;

import io.traffictape.capture.CaptureEngine;
import io.traffictape.capture.ObservedExchange;
import io.traffictape.correlation.ExchangeContext;
import io.traffictape.model.Direction;
import io.traffictape.spring.CaptureContexts;
import io.traffictape.spring.TrafficTapeProperties;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JAX-RS client filter. Request entity is kept when it is already a {@code String} or
 * {@code byte[]}; a POJO is recorded without a body (same gap as WebClient requests).
 */
@Provider
public final class JerseyClientCaptureFilter implements ClientRequestFilter, ClientResponseFilter {

    private static final String SKIP = "traffictape.jersey.skip";
    private static final String START = "traffictape.jersey.start";
    private static final String BODY = "traffictape.jersey.body";
    private static final String SEQUENCE = "traffictape.jersey.sequence";

    private final CaptureEngine engine;
    private final TrafficTapeProperties properties;

    public JerseyClientCaptureFilter(CaptureEngine engine, TrafficTapeProperties properties) {
        this.engine = engine;
        this.properties = properties;
    }

    @Override
    public void filter(ClientRequestContext request) {
        if (CaptureContexts.suppressed()) {
            request.setProperty(SKIP, Boolean.TRUE);
            return;
        }
        CaptureContexts.beginSpringOutbound();
        request.setProperty(START, System.nanoTime());
        ExchangeContext ctx = CaptureContexts.current();
        request.setProperty(SEQUENCE, ctx == null ? null : ctx.nextOutboundSequence());
        request.setProperty(BODY, snapshotRequest(request));
    }

    @Override
    public void filter(ClientRequestContext request, ClientResponseContext response) {
        if (Boolean.TRUE.equals(request.getProperty(SKIP))) {
            return;
        }
        try {
            long start = request.getProperty(START) instanceof Long n ? n : System.nanoTime();
            Integer sequence = request.getProperty(SEQUENCE) instanceof Integer i ? i : null;
            byte[] requestBody = request.getProperty(BODY) instanceof byte[] b ? b : new byte[0];
            Prefix body = Prefix.copy(response.getEntityStream(), properties.getMaxResponseBytes());
            response.setEntityStream(body.stream());
            record(request, requestBody, response, body, sequence, start);
        } catch (Throwable ignored) {
        } finally {
            CaptureContexts.endSpringOutbound();
        }
    }

    private void record(
            ClientRequestContext request,
            byte[] requestBody,
            ClientResponseContext response,
            Prefix responseBody,
            Integer sequence,
            long startNanos) {
        try {
            URI uri = request.getUri();
            String path = uri.getRawPath() == null ? "/" : uri.getRawPath();
            String host = uri.getHost();
            if (host != null && uri.getPort() > 0) {
                host = host + ":" + uri.getPort();
            }
            engine.record(ObservedExchange.builder()
                    .direction(Direction.OUTBOUND)
                    .timestamp(Instant.now())
                    .method(request.getMethod())
                    .path(path)
                    .destination(properties.destinationName(host))
                    .query(ObservedExchange.parseQuery(uri.getRawQuery()))
                    .requestHeaders(toMap(request.getStringHeaders()))
                    .requestContentType(header(request.getStringHeaders(), "Content-Type"))
                    .requestBody(requestBody)
                    .status(response.getStatus())
                    .responseHeaders(toMap(response.getHeaders()))
                    .responseContentType(header(response.getHeaders(), "Content-Type"))
                    .responseBody(responseBody.captured())
                    .responseTruncated(responseBody.truncated())
                    .responseDeclaredSize(responseBody.declaredSize())
                    .latencyMs((System.nanoTime() - startNanos) / 1_000_000L)
                    .exchangeContext(CaptureContexts.current())
                    .outboundSequence(sequence)
                    .build());
        } catch (Throwable ignored) {
        }
    }

    private static byte[] snapshotRequest(ClientRequestContext request) {
        Object entity = request.getEntity();
        if (entity instanceof byte[] bytes) {
            return bytes;
        }
        if (entity instanceof String text) {
            return text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        return new byte[0];
    }

    private static String header(MultivaluedMap<String, String> headers, String name) {
        List<String> values = headers.get(name);
        if (values == null || values.isEmpty()) {
            return headers.entrySet().stream()
                    .filter(e -> e.getKey() != null && e.getKey().equalsIgnoreCase(name))
                    .map(e -> e.getValue().isEmpty() ? null : e.getValue().get(0))
                    .findFirst()
                    .orElse(null);
        }
        return values.get(0);
    }

    private static Map<String, List<String>> toMap(MultivaluedMap<String, String> headers) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        headers.forEach((k, v) -> out.put(k, List.copyOf(v)));
        return out;
    }

    private record Prefix(byte[] captured, boolean truncated, long declaredSize, InputStream stream) {
        static Prefix copy(InputStream in, int max) throws IOException {
            if (in == null) {
                return new Prefix(new byte[0], false, 0, InputStream.nullInputStream());
            }
            byte[] prefix = in.readNBytes(max + 1);
            boolean truncated = prefix.length > max;
            byte[] captured = truncated ? Arrays.copyOf(prefix, max) : prefix;
            InputStream app = truncated
                    ? new SequenceInputStream(new ByteArrayInputStream(captured), in)
                    : new ByteArrayInputStream(prefix);
            return new Prefix(captured, truncated, truncated ? max + 1L : prefix.length, app);
        }
    }
}
