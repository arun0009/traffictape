package io.traffictape.bench;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Measures what capture costs on the request thread by A/B-ing the same endpoint in one warm JVM:
 * requests carrying the exclusion header short-circuit before any wrapping, so the difference is
 * the capture path itself rather than two different contexts or two different JIT states.
 *
 * <p>MockMvc's own cost per request is larger than the capture path, so the printed ratio is a
 * regression signal rather than a measurement of absolute overhead — expect it to sit near 1. The
 * budget catches work landing on the request thread (a second serialization pass, a synchronous
 * write), which shows up as an order of magnitude. {@link SinkIsolationTest} is what actually
 * pins the asynchronous guarantee.
 */
@SpringBootTest(classes = CaptureOverheadTest.App.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.main.banner-mode=off",
        "traffictape.enabled=true",
        "traffictape.output.directory=${java.io.tmpdir}/traffictape-bench",
        "traffictape.flush.interval=30s",
        "traffictape.capture.exclude.request-headers.x-skip-capture[0]=*",
        // Large budget so the sampler never stops capturing mid-measurement.
        "traffictape.max-examples-per-scenario=100000"
})
class CaptureOverheadTest {

    private static final int WARMUP = 200;
    private static final int RUNS = 1000;
    /** Observed around 1x for GET and 1.4x for POST; 6 leaves noise room without being vacuous. */
    private static final int MAX_RATIO = 6;

    @Autowired
    MockMvc mvc;

    @Test
    void captureOverheadOnTheRequestThreadStaysWithinBudget() throws Exception {
        timeGets(WARMUP, true);
        timeGets(WARMUP, false);

        long excludedNs = timeGets(RUNS, true);
        long capturedNs = timeGets(RUNS, false);

        report("GET", excludedNs, capturedNs);
        assertRatio("GET", excludedNs, capturedNs);
    }

    @Test
    void bodyCaptureOverheadStaysWithinBudget() throws Exception {
        timePosts(WARMUP, true);
        timePosts(WARMUP, false);

        long excludedNs = timePosts(RUNS, true);
        long capturedNs = timePosts(RUNS, false);

        report("POST", excludedNs, capturedNs);
        assertRatio("POST", excludedNs, capturedNs);
    }

    private long timeGets(int n, boolean excluded) throws Exception {
        long start = System.nanoTime();
        for (int i = 0; i < n; i++) {
            var request = get("/ping");
            if (excluded) {
                request = request.header("X-Skip-Capture", "true");
            }
            mvc.perform(request).andExpect(status().isOk());
        }
        return System.nanoTime() - start;
    }

    private long timePosts(int n, boolean excluded) throws Exception {
        long start = System.nanoTime();
        for (int i = 0; i < n; i++) {
            var request = post("/echo").contentType("application/json").content("{\"n\":" + i + "}");
            if (excluded) {
                request = request.header("X-Skip-Capture", "true");
            }
            mvc.perform(request).andExpect(status().isOk());
        }
        return System.nanoTime() - start;
    }

    private static void report(String label, long excludedNs, long capturedNs) {
        System.out.printf(
                "%s x%d  excluded %.1f us/req  captured %.1f us/req  ratio %.2fx%n",
                label, RUNS, micros(excludedNs), micros(capturedNs), (double) capturedNs / excludedNs);
    }

    private static void assertRatio(String label, long excludedNs, long capturedNs) {
        assertThat((double) capturedNs / excludedNs)
                .as("%s capture cost per request (%.1f us excluded vs %.1f us captured)",
                        label, micros(excludedNs), micros(capturedNs))
                .isLessThan(MAX_RATIO);
    }

    private static double micros(long totalNs) {
        return TimeUnit.NANOSECONDS.toMicros(totalNs) / (double) RUNS;
    }

    @SpringBootApplication
    static class App {
        @RestController
        static class Api {
            @GetMapping("/ping")
            String ping() {
                return "ok";
            }

            @PostMapping("/echo")
            Map<String, Object> echo(@RequestBody Map<String, Object> body) {
                return body;
            }
        }
    }
}
