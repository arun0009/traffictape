package io.traffictape.capture;

import io.traffictape.model.Direction;
import io.traffictape.model.EventType;
import io.traffictape.model.HttpTransaction;
import io.traffictape.statistics.StatisticsRegistry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectStoreCaptureSinkTest {

    @Test
    void writesSameCorpusLayoutAsFiles() throws Exception {
        Map<String, byte[]> store = new LinkedHashMap<>();
        ObjectStoreCaptureSink sink = new ObjectStoreCaptureSink(
                (path, content, type) -> store.put(path, content),
                Map.of("serviceName", "demo", "output", "s3://bucket/demo"));
        HttpTransaction tx = new HttpTransaction(
                "1", EventType.HTTP_TRANSACTION, Direction.INBOUND, Instant.parse("2026-08-26T22:00:00Z"),
                null, null, "GET", "/widgets/{id}", "/widgets/1", Map.of(), null, "none", "200",
                4, null, null);
        sink.write(new CaptureBatch(List.of(tx), new StatisticsRegistry(10).snapshot()));
        sink.close();

        assertThat(store).containsKeys(
                "metadata.json",
                "statistics.json",
                "gaps.json",
                "fanout.json",
                "FOR_CLAUDE.md",
                "events/events-000001.jsonl.gz");
        String jsonl;
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(store.get("events/events-000001.jsonl.gz")))) {
            jsonl = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(jsonl).contains("\"GET\"").contains("/widgets/{id}");
        String metadata = new String(store.get("metadata.json"), StandardCharsets.UTF_8);
        assertThat(metadata).contains("traffictape").contains("demo").contains("s3://bucket/demo");
    }
}
