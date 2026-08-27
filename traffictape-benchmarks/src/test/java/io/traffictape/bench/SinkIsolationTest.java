package io.traffictape.bench;

import io.traffictape.capture.CaptureBatch;
import io.traffictape.capture.CaptureSink;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Writing never happens on the request thread. A sink slow enough to dominate any
 * synchronous path proves it: if the sink were on the request path, requests could not
 * finish in less than the sink's own cost.
 */
@SpringBootTest(classes = SinkIsolationTest.App.class)
@AutoConfigureMockMvc
@Import(SinkIsolationTest.SlowSinkConfig.class)
@TestPropertySource(properties = {
        "spring.main.banner-mode=off",
        "traffictape.enabled=true",
        "traffictape.output.directory=${java.io.tmpdir}/traffictape-isolation",
        // Flush every event so the slow sink is exercised as often as possible.
        "traffictape.flush.interval=1ms",
        "traffictape.flush.max-events=1",
        "traffictape.max-examples-per-scenario=100000"
})
class SinkIsolationTest {

    private static final int REQUESTS = 50;
    private static final long SINK_DELAY_MS = 50;

    @Autowired
    MockMvc mvc;

    @Autowired
    SlowSink sink;

    @Test
    void aSlowSinkDoesNotSlowDownRequests() throws Exception {
        long start = System.nanoTime();
        for (int i = 0; i < REQUESTS; i++) {
            mvc.perform(get("/ping")).andExpect(status().isOk());
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        long synchronousCost = REQUESTS * SINK_DELAY_MS;
        assertThat(elapsedMs)
                .as("%d requests took %dms; a synchronous sink would have cost at least %dms",
                        REQUESTS, elapsedMs, synchronousCost)
                .isLessThan(synchronousCost / 2);
    }

    @Test
    void theSlowSinkIsActuallyReached() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/ping")).andExpect(status().isOk());
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (sink.batches() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertThat(sink.batches())
                .as("the isolation test is only meaningful if the slow sink really runs")
                .isPositive();
    }

    static final class SlowSink implements CaptureSink {
        private final AtomicInteger batches = new AtomicInteger();

        int batches() {
            return batches.get();
        }

        @Override
        public void write(CaptureBatch batch) {
            if (batch == null || batch.size() == 0) {
                return;
            }
            batches.incrementAndGet();
            sleep();
        }

        @Override
        public void flush() {
            // nothing buffered
        }

        @Override
        public void close() {
            // nothing to release
        }

        private static void sleep() {
            try {
                Thread.sleep(SINK_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @TestConfiguration
    static class SlowSinkConfig {
        @Bean
        SlowSink slowSink() {
            return new SlowSink();
        }
    }

    @SpringBootApplication
    static class App {
        @RestController
        static class Api {
            @GetMapping("/ping")
            String ping() {
                return "ok";
            }
        }
    }
}
