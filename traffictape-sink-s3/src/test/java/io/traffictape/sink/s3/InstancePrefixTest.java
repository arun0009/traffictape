package io.traffictape.sink.s3;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InstancePrefixTest {

    @Test
    void uniquePrefixIncludesDateAndInstance() {
        TrafficTapeS3Properties properties = new TrafficTapeS3Properties();
        properties.setPrefix("payments-api");
        properties.setUniquePerInstance(true);
        String prefix = InstancePrefix.resolve(properties, "ignored");
        assertThat(prefix).matches("payments-api/\\d{4}-\\d{2}-\\d{2}/.+");
    }

    @Test
    void sharedPrefixIsExactlyWhatWasConfigured() {
        TrafficTapeS3Properties properties = new TrafficTapeS3Properties();
        properties.setPrefix("payments-api/qa");
        properties.setUniquePerInstance(false);
        assertThat(InstancePrefix.resolve(properties, "other")).isEqualTo("payments-api/qa");
    }

    @Test
    void blankPrefixFallsBackToServiceName() {
        TrafficTapeS3Properties properties = new TrafficTapeS3Properties();
        properties.setUniquePerInstance(false);
        assertThat(InstancePrefix.resolve(properties, "My Service")).isEqualTo("my-service");
    }
}
