package io.traffictape.model;

import io.traffictape.capture.JsonSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpTransactionGoldenTest {

    @Test
    void schemaVersion1FixtureRoundTrips() throws Exception {
        HttpTransaction tx = JsonSupport.lenientReader().readValue(
                getClass().getResourceAsStream("/golden/v1-http-transaction.json"),
                HttpTransaction.class);
        assertThat(tx.schemaVersion()).isEqualTo(HttpTransaction.SCHEMA_VERSION);
        assertThat(tx.eventType()).isEqualTo(EventType.HTTP_TRANSACTION);
        assertThat(tx.method()).isEqualTo("GET");
        assertThat(tx.route()).isEqualTo("/widgets/{id}");

        HttpTransaction again = JsonSupport.lenientReader().readValue(
                JsonSupport.mapper().writeValueAsBytes(tx), HttpTransaction.class);
        assertThat(again.path()).isEqualTo("/widgets/123");
        assertThat(again.scenarioFingerprintId()).isEqualTo(tx.scenarioFingerprintId());
    }
}
