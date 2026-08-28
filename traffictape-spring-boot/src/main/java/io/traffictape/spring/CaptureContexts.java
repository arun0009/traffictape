package io.traffictape.spring;

import io.traffictape.correlation.ExchangeContext;

/**
 * Request-thread (and Reactor) holder so outbound interceptors can attach to the inbound exchange.
 */
public final class CaptureContexts {

    /** Servlet request attribute holding the inbound {@link ExchangeContext}. */
    public static final String REQUEST_ATTRIBUTE = "traffictape.exchangeContext";
    /**
     * Servlet request attribute holding a JAX-RS {@code @Path} template, set after Jersey
     * matches the resource. The inbound filter prefers this over Spring MVC's pattern.
     */
    public static final String ROUTE_ATTRIBUTE = "traffictape.route";
    /** Reactor context key holding the inbound {@link ExchangeContext}. */
    public static final String REACTOR_KEY = "traffictape.exchangeContext";
    /** Reactor context key marking an exchange whose capture is suppressed. */
    public static final String REACTOR_SUPPRESSED_KEY = "traffictape.suppressed";

    private static final ThreadLocal<ExchangeContext> CURRENT = new ThreadLocal<>();
    /**
     * RestClient/RestTemplate already recorded this hop. OkHttp must not record it again
     * when it is only the transport under those clients.
     */
    private static final ThreadLocal<Integer> SPRING_OUTBOUND = new ThreadLocal<>();
    /**
     * The inbound request was excluded, so outbound calls it causes must be excluded too.
     * Otherwise you get stubs with no parent request.
     */
    private static final ThreadLocal<Boolean> SUPPRESSED = new ThreadLocal<>();

    private CaptureContexts() {
    }

    /**
     * Excludes the current thread from capture, including any outbound calls it makes.
     */
    public static void suppress() {
        SUPPRESSED.set(Boolean.TRUE);
    }

    /**
     * @return {@code true} if capture is suppressed for the current thread
     */
    public static boolean suppressed() {
        return Boolean.TRUE.equals(SUPPRESSED.get());
    }

    /**
     * Binds the inbound exchange that outbound hops on this thread correlate to.
     *
     * @param context the inbound exchange
     */
    public static void set(ExchangeContext context) {
        CURRENT.set(context);
    }

    /**
     * @return the inbound exchange bound to this thread, or {@code null} if none
     */
    public static ExchangeContext current() {
        return CURRENT.get();
    }

    /**
     * Marks entry into a RestClient/RestTemplate hop so OkHttp, acting only as the
     * transport beneath them, does not record the same call again. Reentrant.
     */
    public static void beginSpringOutbound() {
        Integer depth = SPRING_OUTBOUND.get();
        SPRING_OUTBOUND.set(depth == null ? 1 : depth + 1);
    }

    /**
     * Marks exit from a RestClient/RestTemplate hop opened by {@link #beginSpringOutbound()}.
     */
    public static void endSpringOutbound() {
        Integer depth = SPRING_OUTBOUND.get();
        if (depth == null || depth <= 1) {
            SPRING_OUTBOUND.remove();
        } else {
            SPRING_OUTBOUND.set(depth - 1);
        }
    }

    /**
     * @return {@code true} while a RestClient/RestTemplate hop is recording this call
     */
    public static boolean springOutboundActive() {
        Integer depth = SPRING_OUTBOUND.get();
        return depth != null && depth > 0;
    }

    /**
     * Drops all state for the current thread. Must run at the end of every request so
     * nothing leaks into the next request served by a pooled thread.
     */
    public static void clear() {
        CURRENT.remove();
        SPRING_OUTBOUND.remove();
        SUPPRESSED.remove();
    }
}
