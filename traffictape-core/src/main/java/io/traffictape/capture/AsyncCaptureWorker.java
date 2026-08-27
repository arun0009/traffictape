package io.traffictape.capture;

import io.traffictape.model.HttpTransaction;
import io.traffictape.statistics.StatisticsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background consumer. Never runs on the application request thread.
 */
public final class AsyncCaptureWorker implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AsyncCaptureWorker.class);

    private final CaptureQueue queue;
    private final CaptureSink sink;
    private final StatisticsRegistry statistics;
    private final CaptureMetrics metrics;
    private final int maxEvents;
    private final long maxBytes;
    private final Duration flushInterval;
    private final Duration shutdownDrain;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executor;

    public AsyncCaptureWorker(
            CaptureQueue queue,
            CaptureSink sink,
            StatisticsRegistry statistics,
            CaptureMetrics metrics,
            int maxEvents,
            long maxBytes,
            Duration flushInterval,
            Duration shutdownDrain) {
        this.queue = queue;
        this.sink = sink;
        this.statistics = statistics;
        this.metrics = metrics == null ? CaptureMetrics.NOOP : metrics;
        this.maxEvents = Math.max(1, maxEvents);
        this.maxBytes = Math.max(1, maxBytes);
        this.flushInterval = flushInterval == null ? Duration.ofSeconds(30) : flushInterval;
        this.shutdownDrain = shutdownDrain == null ? Duration.ofSeconds(5) : shutdownDrain;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "traffictape-writer");
            t.setDaemon(true);
            return t;
        });
        executor.submit(this::loop);
    }

    private void loop() {
        List<HttpTransaction> batch = new ArrayList<>();
        long bytes = 0;
        long nextFlush = System.nanoTime() + flushInterval.toNanos();
        while (running.get() || queue.size() > 0) {
            try {
                long wait = Math.max(1, nextFlush - System.nanoTime());
                HttpTransaction tx = queue.poll(Duration.ofNanos(wait));
                if (tx != null) {
                    batch.add(tx);
                    bytes += approx(tx);
                }
                metrics.recordQueueSize(queue.size());
                boolean time = System.nanoTime() >= nextFlush;
                boolean full = batch.size() >= maxEvents || bytes >= maxBytes;
                if (!batch.isEmpty() && (time || full || !running.get())) {
                    flush(batch);
                    batch = new ArrayList<>();
                    bytes = 0;
                    nextFlush = System.nanoTime() + flushInterval.toNanos();
                } else if (time) {
                    nextFlush = System.nanoTime() + flushInterval.toNanos();
                    try {
                        sink.write(new CaptureBatch(List.of(), statistics.snapshot()));
                        sink.flush();
                    } catch (Throwable t) {
                        metrics.recordWriteError();
                        statistics.recordWriteError();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                metrics.recordWriteError();
                statistics.recordWriteError();
                log.debug("TrafficTape worker error; continuing", t);
            }
        }
        if (!batch.isEmpty()) {
            flush(batch);
        }
        try {
            sink.flush();
        } catch (Throwable ignored) {
            // fail-open on shutdown
        }
    }

    private void flush(List<HttpTransaction> batch) {
        try {
            sink.write(new CaptureBatch(List.copyOf(batch), statistics.snapshot()));
        } catch (Throwable t) {
            metrics.recordWriteError();
            statistics.recordWriteError();
            log.debug("TrafficTape sink write failed", t);
        }
    }

    private static long approx(HttpTransaction tx) {
        long n = 256;
        if (tx.request() != null && tx.request().body() != null) {
            n += tx.request().body().capturedBytes();
        }
        if (tx.response() != null && tx.response().body() != null) {
            n += tx.response().body().capturedBytes();
        }
        return n;
    }

    @Override
    public void close() {
        queue.stopAccepting();
        running.set(false);
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(shutdownDrain.toMillis(), TimeUnit.MILLISECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        try {
            sink.close();
        } catch (Throwable ignored) {
            // fail-open
        }
    }
}
