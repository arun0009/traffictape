package io.traffictape.capture;

import io.traffictape.model.Direction;
import io.traffictape.model.EventType;
import io.traffictape.model.HttpTransaction;
import io.traffictape.statistics.StatisticsRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncCaptureWorkerTest {

    @Test
    void sinkFailureDoesNotEscapeWorker() throws Exception {
        CaptureQueue queue = new CaptureQueue(10);
        InMemoryCaptureSink sink = new InMemoryCaptureSink();
        sink.fail();
        StatisticsRegistry stats = new StatisticsRegistry(100);
        AsyncCaptureWorker worker = new AsyncCaptureWorker(
                queue, sink, stats, CaptureMetrics.NOOP, 10, 1024,
                Duration.ofMillis(20), Duration.ofSeconds(1));
        worker.start();
        queue.offer(tx());
        Thread.sleep(80);
        worker.close();
        assertThat(stats.writeErrors()).isGreaterThan(0);
    }

    @Test
    void writesBatchToSink() throws Exception {
        CaptureQueue queue = new CaptureQueue(10);
        InMemoryCaptureSink sink = new InMemoryCaptureSink();
        AsyncCaptureWorker worker = new AsyncCaptureWorker(
                queue, sink, new StatisticsRegistry(100), CaptureMetrics.NOOP,
                1, 1024, Duration.ofMillis(20), Duration.ofSeconds(1));
        worker.start();
        queue.offer(tx());
        Thread.sleep(80);
        worker.close();
        assertThat(sink.written()).hasSize(1);
    }

    private static HttpTransaction tx() {
        return new HttpTransaction(
                "1", EventType.HTTP_TRANSACTION, Direction.INBOUND, Instant.now(),
                null, null, "GET", "/x", "/x", null, null, "none", "200", 1, null, null);
    }
}
