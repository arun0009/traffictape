package io.traffictape.spring;

import io.traffictape.capture.CaptureEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = TrafficTapeDisabledTest.App.class,
        properties = "traffictape.enabled=false")
class TrafficTapeDisabledTest {

    @Autowired
    ApplicationContext context;

    @Test
    void doesNotCreateCaptureBeans() {
        assertThat(context.getBeanNamesForType(CaptureEngine.class)).isEmpty();
    }

    @SpringBootApplication
    static class App {
    }
}
