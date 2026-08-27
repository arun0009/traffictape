package io.traffictape.spring.outbound;

import io.traffictape.capture.CaptureEngine;
import io.traffictape.capture.ObservedExchange;
import io.traffictape.correlation.ExchangeContext;
import io.traffictape.model.Direction;
import io.traffictape.spring.CaptureContexts;
import io.traffictape.spring.TrafficTapeProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared RestClient / RestTemplate interceptor. Request body is already a byte[].
 * Response: copy a capped prefix for the corpus; the application still reads the full stream.
 */
public final class OutboundCaptureInterceptor implements ClientHttpRequestInterceptor {

    private final CaptureEngine engine;
    private final TrafficTapeProperties properties;

    public OutboundCaptureInterceptor(CaptureEngine engine, TrafficTapeProperties properties) {
        this.engine = engine;
        this.properties = properties;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        CaptureContexts.beginSpringOutbound();
        try {
            long start = System.nanoTime();
            ExchangeContext ctx = CaptureContexts.current();
            Integer sequence = ctx == null ? null : ctx.nextOutboundSequence();
            ClientHttpResponse response = execution.execute(request, body);
            try {
                PrefixResponse wrapped = PrefixResponse.wrap(response, properties.getMaxResponseBytes());
                record(request, body, wrapped, ctx, sequence, start);
                return wrapped;
            } catch (Throwable t) {
                return response;
            }
        } finally {
            CaptureContexts.endSpringOutbound();
        }
    }

    private void record(
            HttpRequest request,
            byte[] body,
            PrefixResponse response,
            ExchangeContext ctx,
            Integer sequence,
            long startNanos) {
        try {
            URI uri = request.getURI();
            String path = uri.getRawPath() == null ? "/" : uri.getRawPath();
            String host = uri.getHost();
            if (host != null && uri.getPort() > 0) {
                host = host + ":" + uri.getPort();
            }
            byte[] req = body == null ? new byte[0] : body;
            boolean reqTrunc = req.length > properties.getMaxRequestBytes();
            if (reqTrunc) {
                req = Arrays.copyOf(req, properties.getMaxRequestBytes());
            }
            engine.record(ObservedExchange.builder()
                    .direction(Direction.OUTBOUND)
                    .timestamp(Instant.now())
                    .method(request.getMethod().name())
                    .path(path)
                    .destination(properties.destinationName(host))
                    .query(query(uri.getRawQuery()))
                    .requestHeaders(headers(request.getHeaders()))
                    .requestContentType(contentType(request.getHeaders()))
                    .requestBody(req)
                    .requestTruncated(reqTrunc)
                    .requestDeclaredSize(body == null ? 0L : (long) body.length)
                    .status(response.getStatusCode().value())
                    .responseHeaders(headers(response.getHeaders()))
                    .responseContentType(contentType(response.getHeaders()))
                    .responseBody(response.captured())
                    .responseTruncated(response.truncated())
                    .responseDeclaredSize(response.declaredSize())
                    .latencyMs((System.nanoTime() - startNanos) / 1_000_000L)
                    .exchangeContext(ctx)
                    .outboundSequence(sequence)
                    .build());
        } catch (Throwable ignored) {
            // fail-open
        }
    }

    private static String contentType(HttpHeaders headers) {
        return headers.getFirst(HttpHeaders.CONTENT_TYPE);
    }

    private static Map<String, List<String>> headers(HttpHeaders headers) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        headers.forEach((k, v) -> out.put(k, List.copyOf(v)));
        return out;
    }

    private static Map<String, List<String>> query(String raw) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            String name = eq < 0 ? part : part.substring(0, eq);
            String value = eq < 0 ? "" : part.substring(eq + 1);
            out.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        }
        return out;
    }

    static final class PrefixResponse implements ClientHttpResponse {
        private final ClientHttpResponse delegate;
        private final byte[] captured;
        private final boolean truncated;
        private final long declaredSize;
        private final InputStream body;

        private PrefixResponse(
                ClientHttpResponse delegate,
                byte[] captured,
                boolean truncated,
                long declaredSize,
                InputStream body) {
            this.delegate = delegate;
            this.captured = captured;
            this.truncated = truncated;
            this.declaredSize = declaredSize;
            this.body = body;
        }

        static PrefixResponse wrap(ClientHttpResponse response, int maxBytes) throws IOException {
            InputStream in = response.getBody();
            byte[] prefix = in.readNBytes(maxBytes + 1);
            boolean truncated = prefix.length > maxBytes;
            byte[] captured = truncated ? Arrays.copyOf(prefix, maxBytes) : prefix;
            InputStream app = truncated
                    ? new SequenceInputStream(new ByteArrayInputStream(captured), in)
                    : new ByteArrayInputStream(prefix);
            return new PrefixResponse(response, captured, truncated, truncated ? maxBytes + 1L : prefix.length, app);
        }

        byte[] captured() {
            return captured;
        }

        boolean truncated() {
            return truncated;
        }

        long declaredSize() {
            return declaredSize;
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public void close() {
            try {
                body.close();
            } catch (IOException ignored) {
                // ignore
            }
            delegate.close();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public InputStream getBody() {
            return body;
        }
    }
}
