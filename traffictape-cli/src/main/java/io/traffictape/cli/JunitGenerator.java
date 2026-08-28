package io.traffictape.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** JUnit 5 skeleton that replays {@code test-plan.json} against {@code TRAFFICTAPE_BASE_URL}. */
final class JunitGenerator {

    static final String CLASS_NAME = "TrafficTapeReplayTest.java";

    void write(Path directory) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(CLASS_NAME), SOURCE);
    }

    private static final String SOURCE = """
            import com.fasterxml.jackson.databind.JsonNode;
            import com.fasterxml.jackson.databind.ObjectMapper;
            import org.junit.jupiter.params.ParameterizedTest;
            import org.junit.jupiter.params.provider.MethodSource;

            import java.net.URI;
            import java.net.http.HttpClient;
            import java.net.http.HttpRequest;
            import java.net.http.HttpResponse;
            import java.nio.file.Path;
            import java.util.ArrayList;
            import java.util.List;
            import java.util.stream.Stream;

            import static org.junit.jupiter.api.Assertions.assertEquals;

            /**
             * Generated skeleton. Start WireMock from {@code ../wiremock}, then set
             * {@code TRAFFICTAPE_BASE_URL} (default http://localhost:8080).
             */
            class TrafficTapeReplayTest {

                private static final ObjectMapper MAPPER = new ObjectMapper();
                private static final HttpClient HTTP = HttpClient.newHttpClient();
                private static final String BASE = System.getenv().getOrDefault(
                        "TRAFFICTAPE_BASE_URL", "http://localhost:8080");

                static Stream<JsonNode> cases() throws Exception {
                    JsonNode root = MAPPER.readTree(Path.of("test-plan.json").toFile());
                    List<JsonNode> cases = new ArrayList<>();
                    root.get("cases").forEach(cases::add);
                    return cases.stream();
                }

                @ParameterizedTest(name = "{0}")
                @MethodSource("cases")
                void replay(JsonNode testCase) throws Exception {
                    JsonNode request = testCase.get("request");
                    String method = request.get("method").asText();
                    String path = request.has("observedPath")
                            ? request.get("observedPath").asText()
                            : request.get("route").asText();
                    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE + path));
                    if (request.has("body")) {
                        builder.method(method, HttpRequest.BodyPublishers.ofString(request.get("body").toString()));
                    } else {
                        builder.method(method, HttpRequest.BodyPublishers.noBody());
                    }
                    HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                    assertEquals(testCase.get("expect").get("status").asInt(), response.statusCode(),
                            testCase.get("label").asText());
                }
            }
            """;
}
