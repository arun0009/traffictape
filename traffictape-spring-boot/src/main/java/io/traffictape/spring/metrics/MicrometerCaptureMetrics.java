package io.traffictape.spring.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.traffictape.capture.CaptureMetrics;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class MicrometerCaptureMetrics implements CaptureMetrics {

    private final Counter observed;
    private final Counter examples;
    private final Counter dropped;
    private final Counter bytes;
    private final Counter writeErrors;
    private final Counter errors;
    private final Timer captureLatency;
    private final AtomicInteger queueSize = new AtomicInteger();
    private final AtomicInteger endpoints = new AtomicInteger();
    private final AtomicInteger scenarios = new AtomicInteger();
    private final AtomicLong workerLag = new AtomicLong();
    private final AtomicBoolean enabled = new AtomicBoolean();

    public MicrometerCaptureMetrics(MeterRegistry registry) {
        this.observed = Counter.builder("traffictape.requests").description("Observed HTTP exchanges").register(registry);
        this.examples = Counter.builder("traffictape.examples.captured").register(registry);
        this.dropped = Counter.builder("traffictape.events.dropped").register(registry);
        this.bytes = Counter.builder("traffictape.bytes.captured").register(registry);
        this.writeErrors = Counter.builder("traffictape.write.errors").register(registry);
        this.errors = Counter.builder("traffictape.errors").register(registry);
        this.captureLatency = Timer.builder("traffictape.capture.latency").register(registry);
        Gauge.builder("traffictape.queue.size", queueSize, AtomicInteger::get).register(registry);
        Gauge.builder("traffictape.fingerprints", endpoints, AtomicInteger::get).register(registry);
        Gauge.builder("traffictape.scenarios", scenarios, AtomicInteger::get).register(registry);
        Gauge.builder("traffictape.worker.lag", workerLag, AtomicLong::get).register(registry);
        Gauge.builder("traffictape.enabled", enabled, v -> v.get() ? 1 : 0).register(registry);
    }

    @Override
    public void recordObserved(String direction) {
        observed.increment();
    }

    @Override
    public void recordExampleCaptured() {
        examples.increment();
    }

    @Override
    public void recordDropped() {
        dropped.increment();
    }

    @Override
    public void recordBytes(long n) {
        bytes.increment(n);
    }

    @Override
    public void recordWriteError() {
        writeErrors.increment();
    }

    @Override
    public void recordQueueSize(int size) {
        queueSize.set(size);
    }

    @Override
    public void recordFingerprints(int endpointCount, int scenarioCount) {
        endpoints.set(endpointCount);
        scenarios.set(scenarioCount);
    }

    @Override
    public void recordCaptureLatencyNanos(long nanos) {
        captureLatency.record(nanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void recordWorkerLagMs(long millis) {
        workerLag.set(millis);
    }

    @Override
    public void recordError() {
        errors.increment();
    }

    @Override
    public void setEnabled(boolean value) {
        enabled.set(value);
    }
}
