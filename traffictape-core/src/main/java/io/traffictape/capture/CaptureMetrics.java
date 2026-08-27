package io.traffictape.capture;

/**
 * Capture-side metrics. Core has no Micrometer (or any metrics library) dependency.
 *
 * <p>To replace: implement this and expose a {@code @Bean CaptureMetrics}.
 * Core and tests use {@link #NOOP}. Spring uses Micrometer when a {@code MeterRegistry}
 * exists, otherwise {@code NOOP}.
 */
public interface CaptureMetrics {

    CaptureMetrics NOOP = new CaptureMetrics() {
    };

    default void recordObserved(String direction) {
    }

    default void recordExampleCaptured() {
    }

    default void recordDropped() {
    }

    default void recordBytes(long bytes) {
    }

    default void recordWriteError() {
    }

    default void recordQueueSize(int size) {
    }

    default void recordFingerprints(int endpoints, int scenarios) {
    }

    default void recordCaptureLatencyNanos(long nanos) {
    }

    default void recordError() {
    }

    default void setEnabled(boolean enabled) {
    }
}
