package io.traffictape.spring.inbound;

import io.traffictape.capture.CaptureEngine;
import io.traffictape.capture.ObservedExchange;
import io.traffictape.correlation.ExchangeContext;
import io.traffictape.model.Direction;
import io.traffictape.spring.CaptureContexts;
import io.traffictape.spring.TrafficTapeProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Servlet adapter: observes inbound HTTP. Wrapping or capture errors never block the request.
 */
public final class InboundTrafficTapeFilter extends OncePerRequestFilter {

    private static final UrlPathHelper PATHS = new UrlPathHelper();
    private final CaptureEngine engine;
    private final TrafficTapeProperties properties;

    public InboundTrafficTapeFilter(CaptureEngine engine, TrafficTapeProperties properties) {
        this.engine = engine;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isExcludedTraffic(request)) {
            // Outbound calls from this request stay out too. Nothing is wrapped.
            CaptureContexts.suppress();
            try {
                chain.doFilter(request, response);
            } finally {
                CaptureContexts.clear();
            }
            return;
        }

        ExchangeContext ctx = null;
        BoundedRequestWrapper requestWrapper = null;
        TeeResponseWrapper responseWrapper = null;
        HttpServletRequest req = request;
        HttpServletResponse res = response;
        long start = System.nanoTime();
        try {
            ctx = ExchangeContext.open(firstHeaders(request));
            CaptureContexts.set(ctx);
            request.setAttribute(CaptureContexts.REQUEST_ATTRIBUTE, ctx);
            if (shouldWrapRequest(request)) {
                requestWrapper = new BoundedRequestWrapper(request, properties.getMaxRequestBytes());
                req = requestWrapper;
            }
            responseWrapper = new TeeResponseWrapper(response, properties.getMaxResponseBytes());
            res = responseWrapper;
        } catch (Throwable ignored) {
            requestWrapper = null;
            responseWrapper = null;
            req = request;
            res = response;
        }

        try {
            chain.doFilter(req, res);
        } finally {
            try {
                if (responseWrapper != null) {
                    responseWrapper.complete();
                }
                record(request, req, res, requestWrapper, responseWrapper, ctx, start);
            } catch (Throwable ignored) {
            } finally {
                CaptureContexts.clear();
            }
        }
    }

    private void record(
            HttpServletRequest original,
            HttpServletRequest used,
            HttpServletResponse usedResponse,
            BoundedRequestWrapper requestWrapper,
            TeeResponseWrapper responseWrapper,
            ExchangeContext ctx,
            long startNanos) {
        String path = PATHS.getPathWithinApplication(original);
        String method = original.getMethod();
        Object pattern = original.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route = pattern == null ? null : pattern.toString();
        ObservedExchange.Builder observed = ObservedExchange.builder()
                .direction(Direction.INBOUND)
                .timestamp(Instant.now())
                .method(method)
                .path(path)
                .route(route)
                .query(ObservedExchange.parseQuery(original.getQueryString()))
                .requestHeaders(headers(original))
                .responseHeaders(responseHeaders(usedResponse))
                .requestContentType(original.getContentType())
                .responseContentType(usedResponse.getContentType())
                .status(usedResponse.getStatus())
                .latencyMs((System.nanoTime() - startNanos) / 1_000_000L)
                .exchangeContext(ctx);
        if (requestWrapper != null) {
            observed.requestBody(requestWrapper.captured())
                    .requestTruncated(requestWrapper.truncated())
                    .requestDeclaredSize(requestWrapper.declaredSize());
        }
        if (responseWrapper != null) {
            observed.responseBody(responseWrapper.captured())
                    .responseTruncated(responseWrapper.truncated())
                    .responseDeclaredSize(responseWrapper.declaredSize());
        }
        engine.record(observed.build());
    }

    private boolean isExcludedTraffic(HttpServletRequest request) {
        try {
            return !engine.policy().acceptsRequestHeaders(headers(request));
        } catch (Throwable ignored) {
            return true;
        }
    }

    private boolean shouldWrapRequest(HttpServletRequest request) {
        String method = request.getMethod();
        if (method == null) {
            return false;
        }
        String m = method.toUpperCase(Locale.ROOT);
        return "POST".equals(m) || "PUT".equals(m) || "PATCH".equals(m);
    }

    private static Map<String, String> firstHeaders(HttpServletRequest request) {
        Map<String, String> out = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return out;
        }
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            out.put(name, request.getHeader(name));
        }
        return out;
    }

    private static Map<String, List<String>> headers(HttpServletRequest request) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return out;
        }
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            out.put(name, Collections.list(request.getHeaders(name)));
        }
        return out;
    }

    private static Map<String, List<String>> responseHeaders(HttpServletResponse response) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String name : response.getHeaderNames()) {
            out.put(name, List.copyOf(response.getHeaders(name)));
        }
        return out;
    }
}
