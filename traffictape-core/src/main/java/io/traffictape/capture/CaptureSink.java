package io.traffictape.capture;

import java.io.Closeable;

/**
 * Where the tape goes. The worker calls {@link #write(CaptureBatch)} with a batch, never one event.
 * This library ships a file sink and a JSON-line logger. Anything else is a {@code @Bean} of this type.
 */
public interface CaptureSink extends Closeable {

    CaptureSink NOOP = new CaptureSink() {
        @Override
        public void write(CaptureBatch batch) {
        }
    };

    default boolean isDisabled() {
        return false;
    }

    /** Write one flush. Thrown exceptions are caught by the worker, not the request. */
    void write(CaptureBatch batch);

    default void flush() {
    }

    @Override
    default void close() {
    }
}
