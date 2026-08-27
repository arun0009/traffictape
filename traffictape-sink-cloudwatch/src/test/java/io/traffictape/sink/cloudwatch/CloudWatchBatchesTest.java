package io.traffictape.sink.cloudwatch;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudwatchlogs.model.InputLogEvent;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CloudWatchBatchesTest {

    @Test
    void splitsBeforeOneMegabyteBatchLimit() {
        InputLogEvent a = event("a".repeat(400_000));
        InputLogEvent b = event("b".repeat(400_000));
        InputLogEvent c = event("c".repeat(400_000));

        List<List<InputLogEvent>> packed = CloudWatchBatches.pack(List.of(a, b, c));

        assertThat(packed).hasSize(2);
        assertThat(packed.get(0)).containsExactly(a, b);
        assertThat(packed.get(1)).containsExactly(c);
    }

    @Test
    void oneEventFitsAlone() {
        InputLogEvent only = event("x".repeat(800_000));
        assertThat(CloudWatchBatches.pack(List.of(only))).containsExactly(List.of(only));
    }

    @Test
    void rejectsMessagesThatCannotFitOneEvent() {
        String huge = "h".repeat(CloudWatchBatches.MAX_EVENT_BYTES);
        assertThat(CloudWatchBatches.fitsAsSingleEvent(huge)).isFalse();
        assertThat(CloudWatchBatches.fitsAsSingleEvent("{\"ok\":true}")).isTrue();
    }

    private static InputLogEvent event(String message) {
        return InputLogEvent.builder().timestamp(1L).message(message).build();
    }
}
