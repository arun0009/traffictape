package io.traffictape.redaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.traffictape.policy.CapturePolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedactorTest {

    private final Redactor redactor = new DefaultRedactor(CapturePolicy.safeDefaults());
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
    void redactsInsideArraysOfObjects() throws Exception {
        JsonNode out = redactor.json(mapper.readTree(
                "{\"users\":[{\"name\":\"a\",\"password\":\"p1\"},{\"name\":\"b\",\"cvv\":\"123\"}]}"));
        assertThat(out.toString()).doesNotContain("p1").doesNotContain("123");
        assertThat(out.get("users").get(0).get("name").asText()).isEqualTo("a");
        assertThat(out.get("users").get(1).get("cvv").asText()).isEqualTo("[REDACTED]");
    }

    @Test
    void redactsInsideNestedArrays() throws Exception {
        JsonNode out = redactor.json(mapper.readTree("{\"batches\":[[{\"token\":\"t1\"}]]}"));
        assertThat(out.toString()).doesNotContain("t1");
    }

    @Test
    void redactsRootLevelArray() throws Exception {
        JsonNode out = redactor.json(mapper.readTree("[{\"password\":\"p1\"}]"));
        assertThat(out.toString()).doesNotContain("p1");
    }

    @Test
    void redactsNonStringSecretValues() throws Exception {
        JsonNode out = redactor.json(mapper.readTree("{\"cvv\":123,\"token\":{\"a\":\"b\"}}"));
        assertThat(out.toString()).doesNotContain("123").doesNotContain("\"b\"");
    }

    @Test
    void redactsXmlWithCdata() {
        String out = redactor.text(
                "<password><![CDATA[hunter2]]></password>", "application/xml");
        assertThat(out).doesNotContain("hunter2");
    }

    @Test
    void redactsXmlElementContainingChildElements() {
        String out = redactor.text(
                "<token><value>hunter2</value></token>", "application/xml");
        assertThat(out).doesNotContain("hunter2");
    }

    @Test
    void redactsSecretsCarriedInXmlAttributes() {
        String out = redactor.text(
                "<card cardNumber=\"4111111111111111\" cvv='123' brand=\"visa\"/>",
                "application/xml");
        assertThat(out).doesNotContain("4111111111111111").doesNotContain("123");
        assertThat(out).as("non-sensitive attributes survive").contains("visa");
    }

    @Test
    void redactsMultilineXmlElements() {
        String out = redactor.text("<password>\n  hunter2\n</password>", "text/xml");
        assertThat(out).doesNotContain("hunter2");
    }

    @Test
    void redactsXmlWhenContentTypeCarriesCharset() {
        String out = redactor.text(
                "<password>hunter2</password>", "application/soap+xml; charset=utf-8");
        assertThat(out).doesNotContain("hunter2");
    }

    @Test
    void redactsRepeatedAndTrailingFormFields() {
        String out = redactor.text("password=a&password=b&token=c", "application/x-www-form-urlencoded");
        assertThat(out).isEqualTo("password=[REDACTED]&password=[REDACTED]&token=[REDACTED]");
    }

    @Test
    void redactsFormFieldWithEmptyValue() {
        String out = redactor.text("password=&keep=1", "application/x-www-form-urlencoded");
        assertThat(out).isEqualTo("password=[REDACTED]&keep=1");
    }

    @Test
    void aFieldNameIsMatchedRegardlessOfCase() throws Exception {
        JsonNode out = redactor.json(mapper.readTree("{\"PassWord\":\"hunter2\"}"));
        assertThat(out.get("PassWord").asText()).isEqualTo("[REDACTED]");
        assertThat(redactor.headers(Map.of("AUTHORIZATION", List.of("Bearer x"))).get("AUTHORIZATION"))
                .containsExactly("[REDACTED]");
        assertThat(redactor.text("PASSWORD=hunter2", "application/x-www-form-urlencoded"))
                .doesNotContain("hunter2");
    }

    @Test
    void aDenylistedNameInsideAValueIsNotTreatedAsAField() {
        String out = redactor.text("note=my password is safe&keep=1", "application/x-www-form-urlencoded");
        assertThat(out).as("only field names drive redaction, not value text").isEqualTo(
                "note=my password is safe&keep=1");
    }

    @Test
    void disabledRedactionCapturesVerbatim() throws Exception {
        Redactor none = new DefaultRedactor(CapturePolicy.builder().build());
        Map<String, List<String>> headers = none.headers(Map.of("Authorization", List.of("Bearer secret")));
        assertThat(headers.get("Authorization")).containsExactly("Bearer secret");
        assertThat(none.json(mapper.readTree("{\"password\":\"hunter2\"}")).get("password").asText())
                .isEqualTo("hunter2");
        assertThat(none.text("<password>hunter2</password>", "application/xml"))
                .isEqualTo("<password>hunter2</password>");
    }
}
