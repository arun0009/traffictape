package io.traffictape.bench;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CaptureOverheadTest.App.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "traffictape.enabled=true",
        "traffictape.output.directory=${java.io.tmpdir}/traffictape-bench",
        "traffictape.flush.interval=30s",
        "traffictape.max-examples-per-scenario=5"
})
class CaptureOverheadTest {

    @Autowired
    MockMvc mvc;

    @Test
    void enabledGetAndPostComplete() throws Exception {
        int n = 200;
        long start = System.nanoTime();
        for (int i = 0; i < n; i++) {
            mvc.perform(get("/ping")).andExpect(status().isOk());
        }
        long getMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        start = System.nanoTime();
        for (int i = 0; i < n; i++) {
            mvc.perform(post("/echo").contentType("application/json").content("{\"n\":" + i + "}"))
                    .andExpect(status().isOk());
        }
        long postMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        System.out.println("enabled GET " + n + " in " + getMs + "ms; POST " + n + " in " + postMs + "ms");
        assertThat(getMs).isLessThan(30_000);
        assertThat(postMs).isLessThan(30_000);
    }

    @SpringBootApplication
    static class App {
        @RestController
        static class Api {
            @GetMapping("/ping")
            String ping() {
                return "ok";
            }

            @PostMapping("/echo")
            Map<String, Object> echo(@RequestBody Map<String, Object> body) {
                return body;
            }
        }
    }
}
