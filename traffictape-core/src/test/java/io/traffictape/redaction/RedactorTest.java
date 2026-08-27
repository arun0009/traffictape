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

    @Test
    void redactsXmlElements() {
        String out = redactor.text(
                "<login><user>a</user><password>hunter2</password></login>", "application/xml");
        assertThat(out).contains("<user>a</user>");
        assertThat(out).contains("<password>[REDACTED]</password>");
        assertThat(out).doesNotContain("hunter2");
    }

    @Test
    void redactsNamespacedAndAttributedXmlElements() {
        String out = redactor.text(
                "<ns:cvv type=\"str\">123</ns:cvv><ns:amount>5</ns:amount>", "text/xml");
        assertThat(out).contains("<ns:cvv type=\"str\">[REDACTED]</ns:cvv>");
        assertThat(out).contains("<ns:amount>5</ns:amount>");
        assertThat(out).doesNotContain("123");
    }

    @Test
    void redactsFormUrlencodedFields() {
        String out = redactor.text(
                "user=a&password=hunter2&ssn=111-22-3333&keep=1",
                "application/x-www-form-urlencoded");
        assertThat(out).isEqualTo("user=a&password=[REDACTED]&ssn=[REDACTED]&keep=1");
    }

    @Test
    void leavesUnstructuredTextAlone() {
        String out = redactor.text("password: hunter2", "text/plain");
        assertThat(out).isEqualTo("password: hunter2");
    }

    @Test
    void disabledRedactionCapturesVerbatim() throws Exception {
        Redactor none = new Redactor(CapturePolicy.builder().build());
        Map<String, List<String>> headers = none.headers(Map.of("Authorization", List.of("Bearer secret")));
        assertThat(headers.get("Authorization")).containsExactly("Bearer secret");
        assertThat(none.json(mapper.readTree("{\"password\":\"hunter2\"}")).get("password").asText())
                .isEqualTo("hunter2");
        assertThat(none.text("<password>hunter2</password>", "application/xml"))
                .isEqualTo("<password>hunter2</password>");
    }
}
