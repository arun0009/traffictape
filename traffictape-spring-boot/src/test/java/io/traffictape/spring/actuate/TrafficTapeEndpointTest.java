package io.traffictape.spring.actuate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The statistics registry is a singleton for the context's lifetime, so cold-start and
 * post-traffic assertions belong to one ordered test rather than several that would depend on
 * the order JUnit happens to pick.
 */
@SpringBootTest(
        classes = TrafficTapeEndpointTest.App.class,
        properties = {
                "spring.main.banner-mode=off",
                "traffictape.enabled=true",
                "traffictape.output.directory=${java.io.tmpdir}/traffictape-endpoint-it",
                "traffictape.flush.interval=20ms",
                "management.endpoints.web.exposure.include=traffictape"
        })
@AutoConfigureMockMvc
class TrafficTapeEndpointTest {

    @Autowired
    MockMvc mvc;

    @Test
    void readinessGoesFromColdStartToScenariosMissingExamples() throws Exception {
        mvc.perform(get("/actuator/traffictape"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.plateauReached").value(false))
                .andExpect(jsonPath("$.observedRequests").value(0))
                .andExpect(jsonPath("$.lastNewScenarioAt").doesNotExist());

        mvc.perform(get("/widgets/1")).andExpect(status().isOk());
        mvc.perform(get("/widgets/2")).andExpect(status().isOk());

        mvc.perform(get("/actuator/traffictape"))
                .andExpect(status().isOk())
                // Both requests are the same scenario, and neither Actuator call is recorded.
                .andExpect(jsonPath("$.observedRequests").value(2))
                .andExpect(jsonPath("$.uniqueScenarios").value(1))
                .andExpect(jsonPath("$.lastNewScenarioAt").exists())
                .andExpect(jsonPath("$.writeErrors").value(0))
                .andExpect(jsonPath("$.maxExamplesPerScenario").value(50))
                // A body was captured for every observation, so nothing is missing.
                .andExpect(jsonPath("$.scenariosMissingExamples").value(0))
                .andExpect(jsonPath("$.incomplete").isEmpty())
                // Still not ready: behaviour only just appeared, so the plateau has not elapsed.
                .andExpect(jsonPath("$.plateauReached").value(false))
                .andExpect(jsonPath("$.ready").value(false));
    }

    @SpringBootApplication
    static class App {
        @RestController
        static class Widgets {
            @GetMapping("/widgets/{id}")
            Map<String, Object> get(@PathVariable String id) {
                return Map.of("id", id);
            }
        }
    }
}
