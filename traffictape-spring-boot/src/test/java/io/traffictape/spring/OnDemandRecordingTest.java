package io.traffictape.spring;

import com.sun.net.httpserver.HttpServer;
import io.traffictape.capture.InMemoryCaptureSink;
import io.traffictape.model.Direction;
import io.traffictape.model.HttpTransaction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * With a budget of one per scenario, a third request is normally dropped. The on-demand header must
 * record it anyway, along with the outbound call it makes, and neither may carry the header itself.
 */
@SpringBootTest(
        classes = OnDemandRecordingTest.App.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.banner-mode=off",
                "traffictape.enabled=true",
                "traffictape.output.directory=${java.io.tmpdir}/traffictape-on-demand-it",
                "traffictape.flush.interval=20ms",
                "traffictape.flush.max-events=1",
                "traffictape.max-examples-per-scenario=1"
        })
@Import(OnDemandRecordingTest.MemSinkConfig.class)
class OnDemandRecordingTest {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static HttpServer backend;

    @LocalServerPort
    int port;

    @Autowired
    InMemoryCaptureSink sink;

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

    @Test
    void headerRecordsTheExchangeAfterTheScenarioBudgetIsSpent() throws Exception {
        get("/widgets/1", null);
        get("/widgets/2", null);
        awaitEvents(2);
        assertThat(sink.written()).hasSize(2);
        assertThat(sink.written()).allMatch(tx -> tx.correlation().onDemandTag() == null);

        get("/widgets/3", "BUG-1234");
        awaitEvents(4);

        List<HttpTransaction> forced = sink.written().stream()
                .filter(tx -> tx.correlation().onDemandTag() != null)
                .toList();
        assertThat(forced).extracting(tx -> tx.direction() + " " + tx.route())
                .containsExactlyInAnyOrder("INBOUND /widgets/{id}", "OUTBOUND /backend/{id}");
        assertThat(forced).extracting(tx -> tx.correlation().onDemandTag()).containsOnly("BUG-1234");

        HttpTransaction inbound = forced.stream().filter(tx -> tx.direction() == Direction.INBOUND).findFirst().get();
        assertThat(inbound.path()).isEqualTo("/widgets/3");
        assertThat(inbound.request().headers().keySet())
                .noneMatch(name -> name.equalsIgnoreCase("X-TrafficTape-Record"));
    }

    private void get(String path, String tag) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET();
        if (tag != null) {
            request.header("X-TrafficTape-Record", tag);
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
