package io.traffictape.sink.logging;

import io.traffictape.capture.CaptureBatch;
import io.traffictape.model.Direction;
import io.traffictape.model.EventType;
import io.traffictape.model.HttpTransaction;
import io.traffictape.statistics.StatisticsRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingCaptureSinkTest {

    @Test
    void writesOneJsonLinePerTransaction() {
        List<String> lines = new ArrayList<>();
        LoggingCaptureSink sink = new LoggingCaptureSink(lines::add);
        HttpTransaction tx = new HttpTransaction(
                "1", EventType.HTTP_TRANSACTION, Direction.INBOUND, Instant.parse("2026-08-26T22:00:00Z"),
                null, null, "GET", "/widgets/{id}", "/widgets/1", Map.of(), null, "none", "200",
                4, null, null);
        sink.write(new CaptureBatch(List.of(tx, tx), new StatisticsRegistry(10).snapshot()));

        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).contains("\"GET\"").contains("/widgets/{id}").contains("HTTP_TRANSACTION");
        assertThat(lines.get(0)).doesNotContain("\n");
    }

    @Test
    void emptyBatchIsANoOp() {
        List<String> lines = new ArrayList<>();
        new LoggingCaptureSink(lines::add).write(new CaptureBatch(List.of(), new StatisticsRegistry(10).snapshot()));
        assertThat(lines).isEmpty();
    }
}
