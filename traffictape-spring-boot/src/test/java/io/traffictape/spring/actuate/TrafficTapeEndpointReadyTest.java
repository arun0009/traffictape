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
 * The other half of readiness: with the plateau window collapsed, a corpus that is missing nothing
 * reports ready. Kept separate because the plateau is a property of the context.
 */
@SpringBootTest(
        classes = TrafficTapeEndpointReadyTest.App.class,
        properties = {
                "spring.main.banner-mode=off",
                "traffictape.enabled=true",
                "traffictape.output.directory=${java.io.tmpdir}/traffictape-endpoint-ready-it",
                "traffictape.flush.interval=20ms",
                "traffictape.plateau-after=1ms",
                "management.endpoints.web.exposure.include=traffictape"
        })
@AutoConfigureMockMvc
class TrafficTapeEndpointReadyTest {

    @Autowired
    MockMvc mvc;

    @Test
    void reportsReadyOncePlateauedWithNothingMissing() throws Exception {
        mvc.perform(get("/widgets/1")).andExpect(status().isOk());

        mvc.perform(get("/actuator/traffictape"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plateauReached").value(true))
                .andExpect(jsonPath("$.scenariosMissingExamples").value(0))
                .andExpect(jsonPath("$.ready").value(true));
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
