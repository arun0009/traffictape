package io.traffictape.spring;

import io.traffictape.capture.CaptureSink;
import io.traffictape.sink.logging.LoggingCaptureSink;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = TrafficTapeConsoleSinkTest.App.class,
        properties = {
                "spring.main.banner-mode=off",
                "traffictape.enabled=true",
                "traffictape.output.console=true"
        })
class TrafficTapeConsoleSinkTest {

    @Autowired
    CaptureSink sink;

    @Test
    void consoleReplacesTheFileSink() {
        assertThat(sink).isInstanceOf(LoggingCaptureSink.class);
    }

    @SpringBootApplication
    static class App {
    }
}
