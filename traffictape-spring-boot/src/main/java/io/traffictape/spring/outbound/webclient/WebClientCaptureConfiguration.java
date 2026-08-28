package io.traffictape.spring.outbound.webclient;

import io.traffictape.capture.CaptureEngine;
import io.traffictape.correlation.ExchangeContext;
import io.traffictape.spring.CaptureContexts;
import io.traffictape.spring.TrafficTapeProperties;
import io.traffictape.spring.outbound.OutboundObservation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.reactivestreams.Publisher;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ClientHttpRequestDecorator;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Adds outbound capture to every {@link WebClient} built from the auto-configured builder.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(WebClient.class)
@ConditionalOnProperty(prefix = "traffictape", name = "enabled", havingValue = "true")
public class WebClientCaptureConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "trafficTapeWebClientCustomizer")
    WebClientCustomizer trafficTapeWebClientCustomizer(CaptureEngine engine, TrafficTapeProperties properties) {
        WebClientCaptureFilter filter = new WebClientCaptureFilter(engine, properties);
        return builder -> builder.filter(filter);
    }
}

/**
 * Tees the WebClient request as the inserter writes it and the response as the
 * subscriber reads it. SSE responses: metadata only.
 */
final class WebClientCaptureFilter implements ExchangeFilterFunction {

    private final CaptureEngine engine;
    private final TrafficTapeProperties properties;

    WebClientCaptureFilter(CaptureEngine engine, TrafficTapeProperties properties) {
        this.engine = engine;
        this.properties = properties;
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        long start = System.nanoTime();
        ByteArrayOutputStream requestCaptured = new ByteArrayOutputStream();
        AtomicBoolean requestTruncated = new AtomicBoolean();
        AtomicLong requestSize = new AtomicLong();
        AtomicReference<String> requestContentType =
                new AtomicReference<>(request.headers().getFirst("Content-Type"));
        ClientRequest outbound = wrapRequest(request, requestCaptured, requestTruncated, requestSize, requestContentType);
        return next.exchange(outbound)
                .flatMap(response -> Mono.deferContextual(ctxView -> {
                    if (Boolean.TRUE.equals(ctxView.getOrDefault(CaptureContexts.REACTOR_SUPPRESSED_KEY, Boolean.FALSE))) {
                        return Mono.just(response);
                    }
                    ExchangeContext ctx = ctxView.getOrDefault(CaptureContexts.REACTOR_KEY, CaptureContexts.current());
                    Integer sequence = ctx == null ? null : ctx.nextOutboundSequence();
                    if (isStreaming(response)) {
                        record(request, response,
                                requestCaptured.toByteArray(), requestTruncated.get(), requestSize.get(),
                                requestContentType.get(),
                                new byte[0], false, 0, ctx, sequence, start);
                        return Mono.just(response);
                    }
                    ByteArrayOutputStream captured = new ByteArrayOutputStream();
                    AtomicBoolean truncated = new AtomicBoolean();
                    AtomicLong size = new AtomicLong();
                    return Mono.just(response.mutate()
                            .body(flux -> flux
                                    .map(buf -> copyResponse(buf, captured, truncated, size))
                                    .doFinally(sig -> record(
                                            request, response,
                                            requestCaptured.toByteArray(), requestTruncated.get(), requestSize.get(),
                                            requestContentType.get(),
                                            captured.toByteArray(), truncated.get(), size.get(),
                                            ctx, sequence, start)))
                            .build());
                }))
                .contextWrite(context -> {
                    reactor.util.context.Context out = context;
                    ExchangeContext current = CaptureContexts.current();
                    if (current != null) {
                        out = out.put(CaptureContexts.REACTOR_KEY, current);
                    }
                    if (CaptureContexts.suppressed()) {
                        out = out.put(CaptureContexts.REACTOR_SUPPRESSED_KEY, Boolean.TRUE);
                    }
                    return out;
                });
    }

    private ClientRequest wrapRequest(
            ClientRequest request,
            ByteArrayOutputStream captured,
            AtomicBoolean truncated,
            AtomicLong size,
            AtomicReference<String> contentType) {
        try {
            return ClientRequest.from(request)
                    .body((outputMessage, context) -> request.body().insert(
                            new TeeingClientHttpRequest(
                                    outputMessage, captured, truncated, size, contentType,
                                    properties.getMaxRequestBytes()),
                            context))
                    .build();
        } catch (Throwable ignored) {
            return request;
        }
    }

    private DataBuffer copyResponse(
            DataBuffer buf,
            ByteArrayOutputStream captured,
            AtomicBoolean truncated,
            AtomicLong size) {
        int n = buf.readableByteCount();
        byte[] bytes = new byte[n];
        buf.read(bytes);
        org.springframework.core.io.buffer.DataBufferUtils.release(buf);
        size.addAndGet(n);
        int room = properties.getMaxResponseBytes() - captured.size();
        if (room > 0) {
            captured.write(bytes, 0, Math.min(n, room));
        }
        if (n > room) {
            truncated.set(true);
        }
        return DefaultDataBufferFactory.sharedInstance.wrap(bytes);
    }

    private static boolean isStreaming(ClientResponse response) {
        String ct = response.headers().contentType().map(Object::toString).orElse("");
        return ct.contains("event-stream");
    }

    private void record(
            ClientRequest request,
            ClientResponse response,
            byte[] requestBody,
            boolean requestTruncated,
            long requestSize,
            String requestContentType,
            byte[] responseBody,
            boolean responseTruncated,
            long responseSize,
            ExchangeContext ctx,
            Integer sequence,
            long startNanos) {
        Map<String, List<String>> requestHeaders = OutboundObservation.copyHeaders(request.headers());
        if (requestContentType != null && requestHeaders.keySet().stream()
                .noneMatch(k -> "Content-Type".equalsIgnoreCase(k))) {
            requestHeaders = new LinkedHashMap<>(requestHeaders);
            requestHeaders.put("Content-Type", List.of(requestContentType));
        }
        OutboundObservation.record(
                engine, properties,
                request.method().name(), request.url(),
                requestHeaders,
                OutboundObservation.copyHeaders(response.headers().asHttpHeaders()),
                requestContentType,
                response.headers().contentType().map(Object::toString).orElse(null),
                requestBody, requestTruncated, requestSize,
                responseBody, responseTruncated, responseSize,
                response.statusCode().value(), startNanos, ctx, sequence);
    }
}

/**
 * Peeks DataBuffers as the inserter writes them so the original publisher is not consumed.
 */
final class TeeingClientHttpRequest extends ClientHttpRequestDecorator {

    private final ByteArrayOutputStream captured;
    private final AtomicBoolean truncated;
    private final AtomicLong size;
    private final AtomicReference<String> contentType;
    private final int maxBytes;

    TeeingClientHttpRequest(
            ClientHttpRequest delegate,
            ByteArrayOutputStream captured,
            AtomicBoolean truncated,
            AtomicLong size,
            AtomicReference<String> contentType,
            int maxBytes) {
        super(delegate);
        this.captured = captured;
        this.truncated = truncated;
        this.size = size;
        this.contentType = contentType;
        this.maxBytes = maxBytes;
    }

    @Override
    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
        rememberContentType();
        return super.writeWith(Flux.from(body).map(this::peek));
    }

    @Override
    public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
        rememberContentType();
        return super.writeAndFlushWith(Flux.from(body).map(p -> Flux.from(p).map(this::peek)));
    }

    private void rememberContentType() {
        var type = getHeaders().getContentType();
        if (type != null) {
            contentType.compareAndSet(null, type.toString());
        }
    }

    private DataBuffer peek(DataBuffer buf) {
        int n = buf.readableByteCount();
        if (n <= 0) {
            return buf;
        }
        byte[] bytes = new byte[n];
        int pos = buf.readPosition();
        buf.read(bytes);
        buf.readPosition(pos);
        size.addAndGet(n);
        synchronized (captured) {
            int room = maxBytes - captured.size();
            if (room > 0) {
                captured.write(bytes, 0, Math.min(n, room));
            }
            if (n > room) {
                truncated.set(true);
            }
        }
        return buf;
    }
}
