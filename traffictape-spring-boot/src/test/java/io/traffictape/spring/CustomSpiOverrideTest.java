package io.traffictape.spring;

import com.fasterxml.jackson.databind.JsonNode;
import io.traffictape.capture.InMemoryCaptureSink;
import io.traffictape.fingerprint.PathNormalizer;
import io.traffictape.model.HttpTransaction;
import io.traffictape.policy.CapturePolicy;
import io.traffictape.redaction.DefaultRedactor;
import io.traffictape.redaction.Redactor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A {@code PathNormalizer} or {@code Redactor} {@code @Bean} replaces the default.
 */
@SpringBootTest(
        classes = CustomSpiOverrideTest.App.class,
        properties = {
                "spring.main.banner-mode=off",
                "traffictape.enabled=true",
                "traffictape.output.directory=${java.io.tmpdir}/traffictape-spi-it",
                "traffictape.flush.interval=20ms",
                "traffictape.flush.max-events=1"
        })
@AutoConfigureMockMvc
@Import(CustomSpiOverrideTest.CustomSpis.class)
class CustomSpiOverrideTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    InMemoryCaptureSink sink;

    @BeforeEach
    void clear() {
        sink.clear();
    }

    @Test
    void aCustomPathNormalizerTemplatesIdShapesTheDefaultCannotRecognise() throws Exception {
        mvc.perform(get("/accounts/acct_9f8e7d6c/summary")).andExpect(status().isOk());
        awaitEvents(1);

        // Without the override this segment survives normalization and fragments the endpoint.
        assertThat(only().route()).isEqualTo("/accounts/{account}/summary");
    }

    @Test
    void aCustomRedactorCanRedactByValueShapeNotJustFieldName() throws Exception {
        mvc.perform(get("/accounts/acct_1/note")).andExpect(status().isOk());
        awaitEvents(1);

        String body = only().response().body().body().toString();
        assertThat(body)
                .as("a card number in free text is invisible to a field-name denylist")
                .doesNotContain("4111111111111111")
                .contains(Redactor.REDACTED);
    }

    private HttpTransaction only() {
        assertThat(sink.written()).hasSize(1);
        return sink.written().get(0);
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
        @RestController
        static class Accounts {
            @GetMapping("/accounts/{id}/summary")
            Map<String, Object> summary(@PathVariable String id) {
                return Map.of("id", id);
            }

            @GetMapping("/accounts/{id}/note")
            Map<String, Object> note(@PathVariable String id) {
                return Map.of("note", "charged card 4111111111111111 today");
            }
        }
    }

    @TestConfiguration
    static class CustomSpis {

        private static final Pattern ACCOUNT = Pattern.compile("acct_[a-z0-9]+");
        private static final Pattern CARD = Pattern.compile("\\b\\d{13,19}\\b");

        @Bean
        @Primary
        InMemoryCaptureSink inMemoryCaptureSink() {
            return new InMemoryCaptureSink();
        }

        /**
         * Named to replace the auto-configured bean. Returns a template for our own ID shape and
         * ignores the framework route so the test exercises normalization rather than Spring's
         * matched pattern.
         */
        @Bean
        PathNormalizer trafficTapePathNormalizer() {
            return new PathNormalizer() {
                @Override
                public String normalize(String path) {
                    return ACCOUNT.matcher(path).replaceAll("{account}");
                }

                @Override
                public String preferTemplate(String frameworkTemplate, String path) {
                    return normalize(path);
                }
            };
        }

        @Bean
        Redactor trafficTapeRedactor(CapturePolicy policy) {
            return new DefaultRedactor(policy) {
                @Override
                public JsonNode json(JsonNode node) {
                    JsonNode redacted = super.json(node);
                    return redacted == null ? null : maskCardNumbers(redacted);
                }
            };
        }

        private static JsonNode maskCardNumbers(JsonNode node) {
            if (node.isObject()) {
                node.properties().forEach(entry -> {
                    JsonNode value = entry.getValue();
                    if (value.isTextual()) {
                        ((com.fasterxml.jackson.databind.node.ObjectNode) node)
                                .put(entry.getKey(), CARD.matcher(value.asText()).replaceAll(Redactor.REDACTED));
                    } else {
                        maskCardNumbers(value);
                    }
                });
            }
            return node;
        }
    }
}
