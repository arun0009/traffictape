package io.traffictape.redaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.traffictape.policy.CapturePolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedactorTest {

    private final Redactor redactor = new Redactor(CapturePolicy.safeDefaults());
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void redactsAuthorization() {
        Map<String, List<String>> headers = redactor.headers(Map.of(
                "Authorization", List.of("Bearer secret"),
                "Content-Type", List.of("application/json")));
        assertThat(headers.get("Authorization")).containsExactly("[REDACTED]");
        assertThat(headers.get("Content-Type")).containsExactly("application/json");
    }

    @Test
    void redactsPasswordFields() throws Exception {
        JsonNode node = mapper.readTree("{\"user\":\"a\",\"password\":\"hunter2\",\"nested\":{\"token\":\"x\"}}");
        JsonNode out = redactor.json(node);
        assertThat(out.get("user").asText()).isEqualTo("a");
        assertThat(out.get("password").asText()).isEqualTo("[REDACTED]");
        assertThat(out.get("nested").get("token").asText()).isEqualTo("[REDACTED]");
    }
}
