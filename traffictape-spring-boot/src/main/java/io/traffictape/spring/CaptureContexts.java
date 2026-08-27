package io.traffictape.spring;

import io.traffictape.correlation.ExchangeContext;

/**
 * Request-thread (and Reactor) holder so outbound interceptors can attach to the inbound exchange.
 */
public final class CaptureContexts {

    public static final String REQUEST_ATTRIBUTE = "traffictape.exchangeContext";
    public static final String REACTOR_KEY = "traffictape.exchangeContext";
    public static final String REACTOR_SUPPRESSED_KEY = "traffictape.suppressed";

    private static final ThreadLocal<ExchangeContext> CURRENT = new ThreadLocal<>();
    /**
     * RestClient/RestTemplate already recorded this hop. OkHttp must not record it again
     * when it is only the transport under those clients.
     */
    private static final ThreadLocal<Integer> SPRING_OUTBOUND = new ThreadLocal<>();
    /**
     * The inbound request was excluded from the corpus, so the outbound calls it causes must be
     * excluded too. Recording them anyway would leave dependencies with no parent request, which
     * read as real fan-out.
     */
    private static final ThreadLocal<Boolean> SUPPRESSED = new ThreadLocal<>();

    private CaptureContexts() {
    }

    public static void suppress() {
        SUPPRESSED.set(Boolean.TRUE);
    }

    public static boolean suppressed() {
        return Boolean.TRUE.equals(SUPPRESSED.get());
    }

    public static void set(ExchangeContext context) {
        CURRENT.set(context);
    }

    public static ExchangeContext current() {
        return CURRENT.get();
    }

    public static void beginSpringOutbound() {
        Integer depth = SPRING_OUTBOUND.get();
        SPRING_OUTBOUND.set(depth == null ? 1 : depth + 1);
    }

    public static void endSpringOutbound() {
        Integer depth = SPRING_OUTBOUND.get();
        if (depth == null || depth <= 1) {
            SPRING_OUTBOUND.remove();
        } else {
            SPRING_OUTBOUND.set(depth - 1);
        }
    }

    public static boolean springOutboundActive() {
        Integer depth = SPRING_OUTBOUND.get();
        return depth != null && depth > 0;
    }

    public static void clear() {
        CURRENT.remove();
        SPRING_OUTBOUND.remove();
        SUPPRESSED.remove();
    }
}
