package io.traffictape.spring;

import io.traffictape.capture.CaptureEngine;
import io.traffictape.capture.InMemoryCaptureSink;
import io.traffictape.model.Direction;
import io.traffictape.model.HttpTransaction;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = TrafficTapeSpringTest.App.class,
        properties = {
                "spring.main.banner-mode=off",
                "traffictape.enabled=true",
                "traffictape.output.directory=${java.io.tmpdir}/traffictape-it",
                "traffictape.flush.interval=20ms",
                "traffictape.flush.max-events=1",
                "traffictape.max-examples-per-scenario=50"
        })
@AutoConfigureMockMvc
@Import(TrafficTapeSpringTest.MemSinkConfig.class)
class TrafficTapeSpringTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    InMemoryCaptureSink sink;

    @Autowired
    CaptureEngine engine;

    @BeforeEach
    void clear() {
        sink.clear();
    }

    @Test
    void capturesInboundVerbsAndBodies() throws Exception {
        mvc.perform(get("/widgets/1")).andExpect(status().isOk());
        mvc.perform(post("/widgets").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isCreated());
        mvc.perform(put("/widgets/1").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"y\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/widgets/1").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/widgets/1").contentType(MediaType.APPLICATION_JSON).content("{\"owner\":\"team-a\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/widgets/1")).andExpect(status().isNoContent());
        awaitEvents(6);

        assertThat(sink.written()).extracting(HttpTransaction::method)
                .contains("GET", "POST", "PUT", "PATCH", "DELETE");
        HttpTransaction get = inbound("GET");
        assertThat(get.route()).isEqualTo("/widgets/{id}");
        assertThat(get.path()).isEqualTo("/widgets/1");
        assertThat(get.response().status()).isEqualTo(200);

        HttpTransaction post = inbound("POST");
        assertThat(post.request().body().body().toString()).contains("name");

        var patches = sink.written().stream()
                .filter(tx -> tx.direction() == Direction.INBOUND && "PATCH".equals(tx.method()))
                .toList();
        assertThat(patches).hasSize(2);
        assertThat(patches.get(0).scenarioFingerprintId()).isNotEqualTo(patches.get(1).scenarioFingerprintId());
        assertThat(patches.get(0).endpointFingerprintId()).isEqualTo(patches.get(1).endpointFingerprintId());
    }

    @Test
    void redactsAuthorization() throws Exception {
        mvc.perform(get("/widgets/1").header("Authorization", "Bearer super-secret"))
                .andExpect(status().isOk());
        awaitEvents(1);
        HttpTransaction tx = inbound("GET");
        assertThat(tx.request().headers().get("Authorization")).containsExactly("[REDACTED]");
    }

    @Test
    void skipsActuator() throws Exception {
        long before = engine.statistics().observed();
        mvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
        assertThat(engine.statistics().observed()).isEqualTo(before);
    }

    @Test
    void differentIdsShareEndpointFingerprint() throws Exception {
        mvc.perform(get("/widgets/1")).andExpect(status().isOk());
        mvc.perform(get("/widgets/2")).andExpect(status().isOk());
        awaitEvents(2);
        var gets = sink.written().stream()
                .filter(tx -> tx.direction() == Direction.INBOUND && "GET".equals(tx.method()))
                .toList();
        assertThat(gets.get(0).endpointFingerprintId()).isEqualTo(gets.get(1).endpointFingerprintId());
    }

    @Test
    void capturesQueryParameters() throws Exception {
        mvc.perform(get("/widgets/1").param("expand", "true")).andExpect(status().isOk());
        awaitEvents(1);
        assertThat(inbound("GET").query()).containsKey("expand");
    }

    private HttpTransaction inbound(String method) {
        return sink.written().stream()
                .filter(tx -> tx.direction() == Direction.INBOUND && method.equals(tx.method()))
                .findFirst()
                .orElseThrow();
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
        static class Widgets {
            @GetMapping("/widgets/{id}")
            Map<String, Object> get(@PathVariable String id) {
                return Map.of("id", id, "name", "w");
            }

            @PostMapping("/widgets")
            org.springframework.http.ResponseEntity<Map<String, String>> create(@RequestBody Map<String, String> body) {
                return org.springframework.http.ResponseEntity.status(201).body(Map.of("id", "1"));
            }

            @PutMapping("/widgets/{id}")
            Map<String, String> put(@PathVariable String id, @RequestBody Map<String, String> body) {
                return body;
            }

            @PatchMapping("/widgets/{id}")
            Map<String, String> patch(@PathVariable String id, @RequestBody Map<String, String> body) {
                return body;
            }

            @DeleteMapping("/widgets/{id}")
            org.springframework.http.ResponseEntity<Void> delete(@PathVariable String id) {
                return org.springframework.http.ResponseEntity.noContent().build();
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
