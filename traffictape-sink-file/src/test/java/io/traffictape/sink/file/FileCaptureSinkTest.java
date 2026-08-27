package io.traffictape.sink.file;

import io.traffictape.capture.CaptureBatch;
import io.traffictape.model.Direction;
import io.traffictape.model.EventType;
import io.traffictape.model.HttpTransaction;
import io.traffictape.statistics.StatisticsRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class FileCaptureSinkTest {

    @TempDir
    Path temp;

    @Test
    void writesGzipJsonlAndMetadata() throws Exception {
        FileCaptureSink sink = new FileCaptureSink(temp, Map.of("serviceName", "demo"), 10, 1024);
        HttpTransaction tx = new HttpTransaction(
                "1", EventType.HTTP_TRANSACTION, Direction.INBOUND, Instant.parse("2026-08-26T22:00:00Z"),
                null, null, "GET", "/widgets/{id}", "/widgets/1", Map.of(), null, "none", "200",
                4, null, null);
        sink.write(new CaptureBatch(List.of(tx), new StatisticsRegistry(10).snapshot()));
        sink.close();

        assertThat(temp.resolve("metadata.json")).exists();
        assertThat(temp.resolve("statistics.json")).exists();
        assertThat(temp.resolve("gaps.json")).exists();
        assertThat(temp.resolve("fanout.json")).exists();
        assertThat(temp.resolve("FOR_CLAUDE.md")).exists();
        assertThat(Files.readString(temp.resolve("FOR_CLAUDE.md"))).contains("read this first");
        assertThat(Files.readString(temp.resolve("statistics.json"))).contains("captureReady");
        Path eventFile = temp.resolve("events").resolve("events-000001.jsonl.gz");
        assertThat(eventFile).exists();
        String jsonl;
        try (GZIPInputStream in = new GZIPInputStream(Files.newInputStream(eventFile))) {
            jsonl = new String(in.readAllBytes());
        }
        assertThat(jsonl).contains("\"GET\"");
        assertThat(jsonl).contains("/widgets/{id}");
        String metadata = Files.readString(temp.resolve("metadata.json"));
        assertThat(metadata).contains("traffictape");
        assertThat(metadata).contains("demo");
    }
}
