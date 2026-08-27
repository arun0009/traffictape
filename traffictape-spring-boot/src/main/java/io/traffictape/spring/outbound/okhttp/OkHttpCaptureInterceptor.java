package io.traffictape.spring.outbound.okhttp;

import io.traffictape.capture.CaptureEngine;
import io.traffictape.capture.ObservedExchange;
import io.traffictape.correlation.ExchangeContext;
import io.traffictape.model.Direction;
import io.traffictape.spring.CaptureContexts;
import io.traffictape.spring.TrafficTapeProperties;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OkHttp application interceptor. Copies a capped request body so the call can
 * still proceed; {@code peekBody} for the response. Fail-open.
 *
 * <p>When RestClient/RestTemplate already recorded the hop, this interceptor
 * is a no-op so the same call is not stored twice.
 */
public final class OkHttpCaptureInterceptor implements Interceptor {

    private final CaptureEngine engine;
    private final TrafficTapeProperties properties;

    public OkHttpCaptureInterceptor(CaptureEngine engine, TrafficTapeProperties properties) {
        this.engine = engine;
        this.properties = properties;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        if (CaptureContexts.springOutboundActive() || CaptureContexts.suppressed()) {
            return chain.proceed(chain.request());
        }
        Request original = chain.request();
        Request outbound = original;
        byte[] requestCaptured = new byte[0];
        boolean requestTruncated = false;
        long requestSize = 0;
        try {
            CopiedRequest copy = copyRequest(original, properties.getMaxRequestBytes());
            outbound = copy.request;
            requestCaptured = copy.captured;
            requestTruncated = copy.truncated;
            requestSize = copy.declaredSize;
        } catch (Throwable ignored) {
            outbound = original;
        }
        long start = System.nanoTime();
        ExchangeContext ctx = CaptureContexts.current();
        Integer sequence = ctx == null ? null : ctx.nextOutboundSequence();
        Response response = chain.proceed(outbound);
        try {
            record(outbound, requestCaptured, requestTruncated, requestSize, response, ctx, sequence, start);
        } catch (Throwable ignored) {
            // fail-open
        }
        return response;
    }

    private void record(
            Request request,
            byte[] requestBody,
            boolean requestTruncated,
            long requestSize,
            Response response,
            ExchangeContext ctx,
            Integer sequence,
            long startNanos) throws IOException {
        HttpUrl url = request.url();
        String path = url.encodedPath();
        if (path == null || path.isBlank()) {
            path = "/";
        }
        String host = url.host();
        if (url.port() != HttpUrl.defaultPort(url.scheme())) {
            host = host + ":" + url.port();
        }
        byte[] responseCaptured = new byte[0];
        boolean responseTruncated = false;
        long responseSize = 0;
        String responseCt = contentType(response);
        if (!isStreaming(responseCt)) {
            int max = properties.getMaxResponseBytes();
            ResponseBody peeked = response.peekBody(max + 1L);
            byte[] raw = peeked.bytes();
            responseSize = raw.length;
            responseTruncated = raw.length > max;
            responseCaptured = responseTruncated ? Arrays.copyOf(raw, max) : raw;
        }
        engine.record(ObservedExchange.builder()
                .direction(Direction.OUTBOUND)
                .timestamp(Instant.now())
                .method(request.method())
                .path(path)
                .destination(properties.destinationName(host))
                .query(query(url))
                .requestHeaders(headers(request.headers()))
                .requestContentType(contentType(request.body()))
                .requestBody(requestBody)
                .requestTruncated(requestTruncated)
                .requestDeclaredSize(requestSize)
                .status(response.code())
                .responseHeaders(headers(response.headers()))
                .responseContentType(responseCt)
                .responseBody(responseCaptured)
                .responseTruncated(responseTruncated)
                .responseDeclaredSize(responseSize)
                .latencyMs((System.nanoTime() - startNanos) / 1_000_000L)
                .exchangeContext(ctx)
                .outboundSequence(sequence)
                .build());
    }

    static CopiedRequest copyRequest(Request request, int maxBytes) throws IOException {
        RequestBody body = request.body();
        if (body == null) {
            return new CopiedRequest(request, new byte[0], false, 0);
        }
        Buffer buffer = new Buffer();
        body.writeTo(buffer);
        byte[] all = buffer.readByteArray();
        boolean truncated = all.length > maxBytes;
        byte[] captured = truncated ? Arrays.copyOf(all, maxBytes) : all;
        MediaType mediaType = body.contentType();
        Request rebuilt = request.newBuilder()
                .method(request.method(), RequestBody.create(all, mediaType))
                .build();
        return new CopiedRequest(rebuilt, captured, truncated, all.length);
    }

    private static boolean isStreaming(String contentType) {
        return contentType != null && contentType.contains("event-stream");
    }

    private static String contentType(Response response) {
        MediaType mt = response.body() == null ? null : response.body().contentType();
        if (mt != null) {
            return mt.toString();
        }
        return response.header("Content-Type");
    }

    private static String contentType(RequestBody body) {
        if (body == null || body.contentType() == null) {
            return null;
        }
        return body.contentType().toString();
    }

    private static Map<String, List<String>> headers(Headers headers) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String name : headers.names()) {
            out.put(name, headers.values(name));
        }
        return out;
    }

    private static Map<String, List<String>> query(HttpUrl url) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String name : url.queryParameterNames()) {
            out.put(name, new ArrayList<>(url.queryParameterValues(name)));
        }
        return out;
    }

    record CopiedRequest(Request request, byte[] captured, boolean truncated, long declaredSize) {
    }
}
