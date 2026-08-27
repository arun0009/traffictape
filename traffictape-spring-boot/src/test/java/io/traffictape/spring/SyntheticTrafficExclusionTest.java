package io.traffictape.spring;

import com.sun.net.httpserver.HttpServer;
import io.traffictape.capture.CaptureEngine;
import io.traffictape.capture.InMemoryCaptureSink;
import io.traffictape.model.Direction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Synthetic traffic — a smoke-test harness, an uptime monitor — must be excludable by header, and
 * excluding it has to take the outbound calls it caused with it. Recording those would leave
 * dependencies with no parent request, which read as real fan-out.
 *
 * <p>The collaborator is a real server on its own port rather than the application calling itself,
 * so an excluded request leaves nothing behind at all.
 */
@SpringBootTest(
        classes = SyntheticTrafficExclusionTest.App.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.banner-mode=off",
                "traffictape.enabled=true",
                "traffictape.output.directory=${java.io.tmpdir}/traffictape-exclusion-it",
                "traffictape.flush.interval=20ms",
                "traffictape.flush.max-events=1",
                "traffictape.capture.exclude.request-headers.x-smoke-test[0]=*",
                "traffictape.capture.exclude.request-headers.user-agent[0]=*synthetic-monitor*"
        })
@Import(SyntheticTrafficExclusionTest.MemSinkConfig.class)
class SyntheticTrafficExclusionTest {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static HttpServer backend;

    @LocalServerPort
    int port;

    @Autowired
    InMemoryCaptureSink sink;

    @Autowired
    CaptureEngine engine;

    @BeforeAll
    static void startBackend() throws IOException {
        backend = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        backend.createContext("/backend", exchange -> {
            byte[] body = "{\"sku\":\"1\",\"qty\":7}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        backend.start();
    }

    @AfterAll
    static void stopBackend() {
        backend.stop(0);
    }

    @DynamicPropertySource
    static void backendUrl(DynamicPropertyRegistry registry) {
        registry.add("test.backend.url", () -> "http://localhost:" + backend.getAddress().getPort());
    }

    @BeforeEach
    void clear() {
        sink.clear();
    }

    @Test
    void recordsOrdinaryTrafficWithItsOutboundCall() throws Exception {
        get("/widgets/1", null, null);
        awaitEvents(2);

        assertThat(sink.written()).extracting(tx -> tx.direction() + " " + tx.route())
                .containsExactlyInAnyOrder("INBOUND /widgets/{id}", "OUTBOUND /backend/{id}");
    }

    @Test
    void excludesTrafficMarkedByHeaderPresence() throws Exception {
        long before = engine.statistics().observed();

        get("/widgets/2", "X-Smoke-Test", "true");

        assertNothingObserved(before);
    }

    @Test
    void excludesTrafficMatchingAHeaderValuePattern() throws Exception {
        long before = engine.statistics().observed();

        get("/widgets/3", "User-Agent", "synthetic-monitor/2.0");

        assertNothingObserved(before);
    }

    @Test
    void doesNotExcludeAnUnrelatedValueForAPatternedHeader() throws Exception {
        get("/widgets/4", "User-Agent", "Mozilla/5.0");
        awaitEvents(2);

        assertThat(sink.written()).extracting(tx -> tx.direction() + " " + tx.route())
                .containsExactlyInAnyOrder("INBOUND /widgets/{id}", "OUTBOUND /backend/{id}");
    }

    /**
     * The outbound call is the point: it never carries the marker header, so only propagated
     * suppression can keep it out.
     */
    private void assertNothingObserved(long before) throws InterruptedException {
        Thread.sleep(300);
        assertThat(engine.statistics().observed())
                .as("neither the inbound request nor the outbound call it caused may be observed")
                .isEqualTo(before);
        assertThat(sink.written()).noneMatch(tx -> tx.direction() == Direction.OUTBOUND);
        assertThat(sink.written()).isEmpty();
    }

    private void get(String path, String headerName, String headerValue) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET();
        if (headerName != null) {
            request.header(headerName, headerValue);
        }
        HttpResponse<String> response = CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private void awaitEvents(int min) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (sink.written().size() < min && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertThat(sink.written().size()).isGreaterThanOrEqualTo(min);
    }

    @SpringBootApplication
    static class App {

        @Bean
        RestClient restClient(RestClient.Builder builder) {
            return builder.build();
        }

        @RestController
        static class Widgets {
            private final RestClient restClient;
            private final String backendUrl;

            Widgets(RestClient restClient, @Value("${test.backend.url}") String backendUrl) {
                this.restClient = restClient;
                this.backendUrl = backendUrl;
            }

            @GetMapping("/widgets/{id}")
            Map<String, Object> get(@PathVariable String id) {
                Map<?, ?> backendBody = restClient.get()
                        .uri(backendUrl + "/backend/{id}", id)
                        .retrieve()
                        .body(Map.class);
                return Map.of("id", id, "backend", backendBody);
            }
        }
    }

    @TestConfiguration
    static class MemSinkConfig {
        @Bean
        @Primary
        InMemoryCaptureSink inMemoryCaptureSink() {
            return new InMemoryCaptureSink();
        }
    }
}
