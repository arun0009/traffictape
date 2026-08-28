package io.traffictape.spring.outbound.jersey;

import io.traffictape.capture.CaptureEngine;
import io.traffictape.spring.CaptureContexts;
import io.traffictape.spring.TrafficTapeProperties;
import io.traffictape.spring.outbound.BoundedPrefix;
import io.traffictape.spring.outbound.OutboundObservation;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;

import java.nio.charset.StandardCharsets;
import java.util.List;

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
        var ctx = CaptureContexts.current();
        request.setProperty(SEQUENCE, ctx == null ? null : ctx.nextOutboundSequence());
        request.setProperty(BODY, snapshotRequest(request, properties.getMaxRequestBytes()));
    }

    @Override
    public void filter(ClientRequestContext request, ClientResponseContext response) {
        if (Boolean.TRUE.equals(request.getProperty(SKIP))) {
            return;
        }
        try {
            long start = request.getProperty(START) instanceof Long n ? n : System.nanoTime();
            Integer sequence = request.getProperty(SEQUENCE) instanceof Integer i ? i : null;
            OutboundObservation.CappedBody requestBody = request.getProperty(BODY) instanceof OutboundObservation.CappedBody b
                    ? b : OutboundObservation.CappedBody.empty();
            BoundedPrefix body = BoundedPrefix.copy(response.getEntityStream(), properties.getMaxResponseBytes());
            response.setEntityStream(body.stream());
            OutboundObservation.record(
                    engine, properties,
                    request.getMethod(), request.getUri(),
                    OutboundObservation.copyHeaders(request.getStringHeaders()),
                    OutboundObservation.copyHeaders(response.getHeaders()),
                    header(request.getStringHeaders(), "Content-Type"),
                    header(response.getHeaders(), "Content-Type"),
                    requestBody.bytes(), requestBody.truncated(), requestBody.declaredSize(),
                    body.captured(), body.truncated(), body.declaredSize(),
                    response.getStatus(), start, CaptureContexts.current(), sequence);
        } catch (Throwable ignored) {
        } finally {
            CaptureContexts.endSpringOutbound();
        }
    }

    private static OutboundObservation.CappedBody snapshotRequest(ClientRequestContext request, int maxBytes) {
        Object entity = request.getEntity();
        byte[] raw;
        if (entity instanceof byte[] bytes) {
            raw = bytes;
        } else if (entity instanceof String text) {
            raw = text.getBytes(StandardCharsets.UTF_8);
        } else {
            return OutboundObservation.CappedBody.empty();
        }
        return OutboundObservation.capBody(raw, maxBytes);
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
}
