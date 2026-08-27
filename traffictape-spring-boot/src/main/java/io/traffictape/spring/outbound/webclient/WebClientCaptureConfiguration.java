package io.traffictape.spring.outbound.webclient;

import io.traffictape.capture.CaptureEngine;
import io.traffictape.capture.ObservedExchange;
import io.traffictape.correlation.ExchangeContext;
import io.traffictape.model.Direction;
import io.traffictape.spring.CaptureContexts;
import io.traffictape.spring.TrafficTapeProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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
 * Tees the WebClient response as the subscriber reads it. Request body is not
 * re-materialized (Publisher). SSE/octet-stream: metadata only.
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
        return next.exchange(request)
                .flatMap(response -> Mono.deferContextual(ctxView -> {
                    if (ctxView.getOrDefault(CaptureContexts.REACTOR_SUPPRESSED_KEY, Boolean.FALSE)) {
                        return Mono.just(response);
                    }
                    ExchangeContext ctx = ctxView.getOrDefault(CaptureContexts.REACTOR_KEY, CaptureContexts.current());
                    Integer sequence = ctx == null ? null : ctx.nextOutboundSequence();
                    if (isStreaming(response)) {
                        record(request, response, new byte[0], false, 0, ctx, sequence, start);
                        return Mono.just(response);
                    }
                    ByteArrayOutputStream captured = new ByteArrayOutputStream();
                    AtomicBoolean truncated = new AtomicBoolean();
                    AtomicLong size = new AtomicLong();
                    Flux<DataBuffer> teed = response.bodyToFlux(DataBuffer.class)
                            .map(buf -> copy(buf, captured, truncated, size))
                            .doFinally(sig -> record(
                                    request, response, captured.toByteArray(), truncated.get(), size.get(),
                                    ctx, sequence, start));
                    return Mono.just(response.mutate().body(teed).build());
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

    private DataBuffer copy(
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
            byte[] body,
            boolean truncated,
            long size,
            ExchangeContext ctx,
            Integer sequence,
            long startNanos) {
        try {
            URI uri = request.url();
            String path = uri.getRawPath() == null ? "/" : uri.getRawPath();
            String host = uri.getHost();
            if (host != null && uri.getPort() > 0) {
                host = host + ":" + uri.getPort();
            }
            engine.record(ObservedExchange.builder()
                    .direction(Direction.OUTBOUND)
                    .timestamp(Instant.now())
                    .method(request.method().name())
                    .path(path)
                    .destination(properties.destinationName(host))
                    .query(ObservedExchange.parseQuery(uri.getRawQuery()))
                    .requestHeaders(toMap(request.headers()))
                    .requestContentType(request.headers().getFirst("Content-Type"))
                    .status(response.statusCode().value())
                    .responseHeaders(toMap(response.headers().asHttpHeaders()))
                    .responseContentType(response.headers().contentType().map(Object::toString).orElse(null))
                    .responseBody(body)
                    .responseTruncated(truncated)
                    .responseDeclaredSize(size)
                    .latencyMs((System.nanoTime() - startNanos) / 1_000_000L)
                    .exchangeContext(ctx)
                    .outboundSequence(sequence)
                    .build());
        } catch (Throwable ignored) {
        }
    }

    private static Map<String, List<String>> toMap(org.springframework.http.HttpHeaders headers) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        headers.forEach((k, v) -> out.put(k, List.copyOf(v)));
        return out;
    }
}
