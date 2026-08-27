package io.traffictape.cli;

import io.traffictape.model.BodyCapture;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StubSupportTest {

    @Test
    void convertsTemplatedRouteToRegex() {
        assertThat(StubSupport.routeToRegex("/assets/{id}")).isEqualTo("/assets/[^/]+");
        assertThat(StubSupport.routeToRegex("/a/{x}/b/{y}")).isEqualTo("/a/[^/]+/b/[^/]+");
    }

    @Test
    void escapesRegexMetacharactersInLiterals() {
        String regex = StubSupport.routeToRegex("/v1.0/items+all/{id}");
        assertThat(regex).isEqualTo("/v1\\.0/items\\+all/[^/]+");
        assertThat("/v1.0/items+all/9".matches(regex)).isTrue();
        assertThat("/v1X0/items+all/9".matches(regex)).isFalse();
    }

    @Test
    void templatedRouteRegexDoesNotSpanSegments() {
        String regex = StubSupport.routeToRegex("/assets/{id}");
        assertThat("/assets/9".matches(regex)).isTrue();
        assertThat("/assets/9/children".matches(regex)).isFalse();
    }

    @Test
    void detectsUntemplatedRoutes() {
        assertThat(StubSupport.isTemplated("/orders")).isFalse();
        assertThat(StubSupport.isTemplated("/orders/{id}")).isTrue();
    }

    @Test
    void dropsRedactedAndHopByHopResponseHeaders() {
        Map<String, Object> headers = StubSupport.responseHeaders(Map.of(
                "Content-Type", List.of("application/json"),
                "Set-Cookie", List.of("[REDACTED]"),
                "Transfer-Encoding", List.of("chunked"),
                "Content-Length", List.of("42"),
                "X-Trace", List.of("a", "b")));
        assertThat(headers).containsEntry("Content-Type", "application/json");
        assertThat(headers).containsEntry("X-Trace", List.of("a", "b"));
        assertThat(headers).doesNotContainKeys("Set-Cookie", "Transfer-Encoding", "Content-Length");
    }

    @Test
    void readsTopLevelJsonFieldNames() {
        BodyCapture body = BodyCapture.json(Map.of("status", "OK", "owner", "a"), false, 10, 10);
        assertThat(StubSupport.topLevelJsonFields(body)).containsExactlyInAnyOrder("status", "owner");
    }

    @Test
    void ignoresNonObjectAndOmittedBodies() {
        assertThat(StubSupport.topLevelJsonFields(BodyCapture.json(List.of(1, 2), false, 4, 4))).isEmpty();
        assertThat(StubSupport.topLevelJsonFields(BodyCapture.omitted(99))).isEmpty();
        assertThat(StubSupport.topLevelJsonFields(null)).isEmpty();
    }
}
