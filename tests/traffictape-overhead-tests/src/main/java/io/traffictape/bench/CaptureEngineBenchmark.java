package io.traffictape.bench;

import io.traffictape.capture.CaptureEngine;
import io.traffictape.capture.CaptureQueue;
import io.traffictape.capture.ObservedExchange;
import io.traffictape.model.Direction;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Core-path overhead once sampling is saturated (stats only, no body enqueue).
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class CaptureEngineBenchmark {

    private CaptureEngine engine;
    private ObservedExchange exchange;

    @Setup
    public void setup() {
        CaptureQueue queue = new CaptureQueue(1024);
        engine = CaptureEngine.createDefault(queue, 1);
        exchange = ObservedExchange.builder()
                .direction(Direction.INBOUND)
                .timestamp(Instant.now())
                .method("GET")
                .path("/accounts/123")
                .status(200)
                .query(Map.of())
                .responseContentType("application/json")
                .responseBody("{\"id\":123}".getBytes())
                .build();
        engine.record(exchange);
    }

    @Benchmark
    public void recordSaturatedGet() {
        engine.record(exchange);
    }
}
