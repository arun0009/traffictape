package io.traffictape.spring.outbound;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboundObservationTest {

    @Test
    void capBodyRecordsTruncationAndDeclaredSize() {
        OutboundObservation.CappedBody small = OutboundObservation.capBody(new byte[]{1, 2, 3}, 8);
        assertThat(small.truncated()).isFalse();
        assertThat(small.declaredSize()).isEqualTo(3);

        OutboundObservation.CappedBody large = OutboundObservation.capBody(new byte[]{1, 2, 3, 4, 5}, 3);
        assertThat(large.truncated()).isTrue();
        assertThat(large.declaredSize()).isEqualTo(5);
        assertThat(large.bytes()).hasSize(3);
    }
}
