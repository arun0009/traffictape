package io.traffictape;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrafficTapeVersionTest {

    @Test
    void resolvesFilteredBuildVersion() {
        assertThat(TrafficTapeVersion.get())
                .isNotBlank()
                .isNotEqualTo("unknown")
                .doesNotContain("${");
    }
}
