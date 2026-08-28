package io.traffictape.spring.outbound;

import io.traffictape.capture.CaptureEngine;
import io.traffictape.correlation.ExchangeContext;
import io.traffictape.spring.CaptureContexts;
import io.traffictape.spring.TrafficTapeProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.io.InputStream;

/** Shared RestClient / RestTemplate interceptor. */
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
        if (CaptureContexts.suppressed()) {
            return execution.execute(request, body);
        }
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
            OutboundObservation.CappedBody req = OutboundObservation.capBody(body, properties.getMaxRequestBytes());
            OutboundObservation.record(
                    engine, properties,
                    request.getMethod().name(), request.getURI(),
                    OutboundObservation.copyHeaders(request.getHeaders()),
                    OutboundObservation.copyHeaders(response.getHeaders()),
                    request.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE),
                    response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE),
                    req.bytes(), req.truncated(), req.declaredSize(),
                    response.captured(), response.truncated(), response.declaredSize(),
                    response.getStatusCode().value(), startNanos, ctx, sequence);
        } catch (Exception ignored) {
        }
    }

    static final class PrefixResponse implements ClientHttpResponse {
        private final ClientHttpResponse delegate;
        private final BoundedPrefix prefix;

        private PrefixResponse(ClientHttpResponse delegate, BoundedPrefix prefix) {
            this.delegate = delegate;
            this.prefix = prefix;
        }

        static PrefixResponse wrap(ClientHttpResponse response, int maxBytes) throws IOException {
            return new PrefixResponse(response, BoundedPrefix.copy(response.getBody(), maxBytes));
        }

        byte[] captured() {
            return prefix.captured();
        }

        boolean truncated() {
            return prefix.truncated();
        }

        long declaredSize() {
            return prefix.declaredSize();
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
                prefix.stream().close();
            } catch (IOException ignored) {
            }
            delegate.close();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public InputStream getBody() {
            return prefix.stream();
        }
    }
}
