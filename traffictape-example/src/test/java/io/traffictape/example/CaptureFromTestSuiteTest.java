package io.traffictape.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.traffictape.capture.AsyncCaptureWorker;
import io.traffictape.cli.TrafficTapeCli;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole loop against an existing test suite: drive the application over HTTP, let capture write
 * a corpus, then generate mocks from it — no deployment, no waiting for QA traffic.
 *
 * <p>This is the documented on-ramp in {@code docs/capture-from-tests.md}. It runs in the normal
 * build, so the workflow cannot silently rot.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CaptureFromTestSuiteTest {

    // Created eagerly: @DynamicPropertySource runs before JUnit resolves a static @TempDir.
    private static final Path WORK = createWorkDirectory();
    private static final Path CORPUS = WORK.resolve("corpus");
    private static final Path GENERATED = WORK.resolve("generated");

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @DynamicPropertySource
    static void captureIntoTempDirectory(DynamicPropertyRegistry registry) {
        registry.add("traffictape.enabled", () -> true);
        registry.add("traffictape.output.directory", CORPUS::toString);
        // A test suite is short-lived: flush eagerly rather than on the 30s interval.
        registry.add("traffictape.flush.max-events", () -> 1);
        registry.add("traffictape.flush.interval", () -> "100ms");
        registry.add("traffictape.max-examples-per-scenario", () -> 5);
    }

    @LocalServerPort
    int port;

    @Autowired
    AsyncCaptureWorker worker;

    @AfterAll
    static void cleanUp() throws IOException {
        if (Files.exists(WORK)) {
            try (Stream<Path> paths = Files.walk(WORK)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        }
    }

    @Test
    void capturesTestTrafficThenGeneratesMocksAndATestPlan() throws Exception {
        send("GET", "/widgets/1", null);
        send("POST", "/widgets", "{\"sku\":\"abc\"}");
        send("PATCH", "/widgets/1", "{\"status\":\"ACTIVE\"}");
        send("PATCH", "/widgets/1", "{\"owner\":\"team-a\"}");

        // Closing the worker drains the queue and closes the sink, which is what makes the corpus
        // readable here and now. A normal suite does not need this: the JVM exiting at the end of
        // `mvn test` shuts the context down and drains it the same way.
        worker.close();

        assertThat(CORPUS.resolve("metadata.json")).exists();
        assertThat(CORPUS.resolve("statistics.json")).exists();
        assertThat(eventFiles()).isNotEmpty();

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(captured, true, StandardCharsets.UTF_8);
        int status = TrafficTapeCli.run(new String[]{
                "generate", "--corpus", CORPUS.toString(), "--out", GENERATED.toString()}, out, out);
        assertThat(status).isZero();

        String stubs = readAll(GENERATED.resolve("wiremock").resolve("mappings"));
        assertThat(stubs)
                .as("the outbound calls the controller made must become stubs")
                .contains("/external/inventory/[^/]+")
                .contains("/external/catalog");

        JsonNode plan = MAPPER.readTree(Files.readString(GENERATED.resolve("test-plan.json")));
        List<String> labels = new ArrayList<>();
        plan.get("cases").forEach(node -> labels.add(node.get("label").asText()));

        assertThat(labels)
                .as("two PATCH bodies are two scenarios, not one endpoint")
                .anyMatch(label -> label.contains("PATCH /widgets/{id}") && label.contains("status"))
                .anyMatch(label -> label.contains("PATCH /widgets/{id}") && label.contains("owner"));

        JsonNode widgetGet = caseFor(plan, "INBOUND GET /widgets/{id}");
        assertThat(widgetGet.get("expect").get("status").asInt()).isEqualTo(200);
        assertThat(widgetGet.get("dependsOn").toString())
                .as("the inbound request must be linked to the outbound call it caused")
                .contains("/external/inventory/{id}");
    }

    private static JsonNode caseFor(JsonNode plan, String labelPrefix) {
        for (JsonNode node : plan.get("cases")) {
            if (node.get("label").asText().startsWith(labelPrefix)) {
                return node;
            }
        }
        throw new AssertionError("No test-plan case for " + labelPrefix);
    }

    private void send(String method, String path, String body) throws Exception {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .method(method, publisher);
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        HttpResponse<String> response = CLIENT.send(request.build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isLessThan(400);
    }

    private static List<Path> eventFiles() throws IOException {
        try (Stream<Path> files = Files.list(CORPUS.resolve("events"))) {
            return files.toList();
        }
    }

    private static String readAll(Path directory) throws IOException {
        StringBuilder all = new StringBuilder();
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.toList()) {
                all.append(Files.readString(file));
            }
        }
        return all.toString();
    }

    private static Path createWorkDirectory() {
        try {
            return Files.createTempDirectory("traffictape-example-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
