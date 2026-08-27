package io.traffictape.sink.cloudwatch;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StreamNameTest {

    @Test
    void configuredStreamWins() {
        TrafficTapeCloudWatchProperties properties = new TrafficTapeCloudWatchProperties();
        properties.setLogStream("payments-task-a");
        assertThat(StreamName.resolve(properties)).isEqualTo("payments-task-a");
    }

    @Test
    void stripsColonAndStar() {
        assertThat(StreamName.sanitize("host:1*x")).isEqualTo("host-1-x");
    }
}
