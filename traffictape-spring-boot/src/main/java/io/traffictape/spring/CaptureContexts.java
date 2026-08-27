package io.traffictape.spring;

import io.traffictape.correlation.ExchangeContext;

/**
 * Request-thread (and Reactor) holder so outbound interceptors can attach to the inbound exchange.
 */
public final class CaptureContexts {

    public static final String REQUEST_ATTRIBUTE = "traffictape.exchangeContext";
    public static final String REACTOR_KEY = "traffictape.exchangeContext";

    private static final ThreadLocal<ExchangeContext> CURRENT = new ThreadLocal<>();
    /**
     * RestClient/RestTemplate already recorded this hop. OkHttp must not record it again
     * when it is only the transport under those clients.
     */
    private static final ThreadLocal<Integer> SPRING_OUTBOUND = new ThreadLocal<>();

    private CaptureContexts() {
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
    }
}
