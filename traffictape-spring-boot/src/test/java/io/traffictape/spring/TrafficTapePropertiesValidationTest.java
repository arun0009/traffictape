package io.traffictape.spring;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrafficTapePropertiesValidationTest {

    @Test
    void rejectsNonPositiveQueueSize() {
        TrafficTapeProperties properties = new TrafficTapeProperties();
        properties.setQueueSize(0);
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("queue-size");
    }

    @Test
    void rejectsBlankOutputDirectory() {
        TrafficTapeProperties properties = new TrafficTapeProperties();
        properties.getOutput().setDirectory("  ");
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("directory");
    }

    @Test
    void rejectsZeroFlushInterval() {
        TrafficTapeProperties properties = new TrafficTapeProperties();
        properties.getFlush().setInterval(Duration.ZERO);
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("flush.interval");
    }

    @Test
    void acceptsZeroPlateauAfter() {
        TrafficTapeProperties properties = new TrafficTapeProperties();
        properties.setPlateauAfter(Duration.ZERO);
        properties.validate();
    }
}
