package io.traffictape.body;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.traffictape.model.BodyCapture;
import io.traffictape.model.BodyEncoding;
import io.traffictape.policy.CapturePolicy;
import io.traffictape.redaction.Redactor;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class BodyCodecTest {

    private final BodyCodec codec = new BodyCodec(new ObjectMapper(), new Redactor(CapturePolicy.safeDefaults()), 16);
    private final BodyCodec jsonCodec = new BodyCodec(new ObjectMapper(), new Redactor(CapturePolicy.safeDefaults()), 1024);

    @Test
    void emptyBody() {
        assertThat(codec.decode(new byte[0], "application/json", false, 0L).encoding()).isEqualTo(BodyEncoding.EMPTY);
    }

    @Test
    void truncatesOverLimit() {
        BodyCapture capture = codec.decode("abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8),
                "text/plain", false, 26L);
        assertThat(capture.truncated()).isTrue();
        assertThat(capture.capturedBytes()).isEqualTo(16);
        assertThat(capture.sizeBytes()).isEqualTo(26);
    }

    @Test
    void omitsBinary() {
        BodyCapture capture = codec.decode(new byte[]{1, 2, 3}, "application/octet-stream", false, 3L);
        assertThat(capture.encoding()).isEqualTo(BodyEncoding.OMITTED);
        assertThat(capture.body()).isNull();
    }

    @Test
    void jsonIsRedacted() {
        BodyCapture capture = jsonCodec.decode("{\"password\":\"x\",\"ok\":true}".getBytes(), "application/json", false, 30L);
        assertThat(capture.encoding()).isEqualTo(BodyEncoding.JSON);
        assertThat(capture.body().toString()).contains("[REDACTED]");
        assertThat(capture.body().toString()).doesNotContain("\"x\"");
    }

    @Test
    void xmlIsRedacted() {
        BodyCapture capture = jsonCodec.decode(
                "<r><password>hunter2</password></r>".getBytes(StandardCharsets.UTF_8),
                "application/xml", false, 34L);
        assertThat(capture.encoding()).isEqualTo(BodyEncoding.TEXT);
        assertThat(capture.body().toString()).doesNotContain("hunter2");
    }

    @Test
    void omitsUnparseableJsonRatherThanStoringItRaw() {
        BodyCapture capture = jsonCodec.decode(
                "{\"password\":\"hunter2\", truncated".getBytes(StandardCharsets.UTF_8),
                "application/json", true, 500L);
        assertThat(capture.encoding()).isEqualTo(BodyEncoding.OMITTED);
        assertThat(capture.body()).isNull();
        assertThat(capture.sizeBytes()).isEqualTo(500);
    }

    @Test
    void omitsTextBodiesWhenDisabled() {
        BodyCodec noText = new BodyCodec(
                new ObjectMapper(), new Redactor(CapturePolicy.safeDefaults()), 1024, false);
        BodyCapture capture = noText.decode("<r>x</r>".getBytes(StandardCharsets.UTF_8),
                "application/xml", false, 8L);
        assertThat(capture.encoding()).isEqualTo(BodyEncoding.OMITTED);
    }
}
