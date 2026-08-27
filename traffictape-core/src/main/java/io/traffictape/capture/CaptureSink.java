package io.traffictape.capture;

import java.io.Closeable;

/**
 * Pluggable output. The worker calls {@link #write(CaptureBatch)}; it never
 * ships one event at a time.
 *
 * <p>Shipped sinks: file (default), S3, CloudWatch. That is the closed set.
 * Anything else is this interface plus a {@code @Bean CaptureSink} — core
 * does not change.
 */
public interface CaptureSink extends Closeable {

    CaptureSink NOOP = new CaptureSink() {
        @Override
        public void write(CaptureBatch batch) {
        }
    };

    /**
     * Persist one flush of events plus a statistics snapshot. Implementations must not throw
     * in a way that reaches application request threads — the worker already catches.
     */
    void write(CaptureBatch batch);

    default void flush() {
    }

    @Override
    default void close() {
    }
}
