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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
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

    @Test
    void aPathThatCannotBeCreatedDisablesTheSinkInsteadOfThrowing() throws Exception {
        Path blocked = temp.resolve("blocked");
        Files.writeString(blocked, "not a directory");
        FileCaptureSink sink = new FileCaptureSink(blocked, Map.of());
        assertThat(sink.isDisabled()).isTrue();
        HttpTransaction tx = new HttpTransaction(
                "1", EventType.HTTP_TRANSACTION, Direction.INBOUND, Instant.parse("2026-08-26T22:00:00Z"),
                null, null, "GET", "/x", "/x", Map.of(), null, "none", "200",
                4, null, null);
        sink.write(new CaptureBatch(List.of(tx), new StatisticsRegistry(10).snapshot()));
        sink.close();
        assertThat(Files.isDirectory(blocked)).isFalse();
    }

    @Test
    void secondSinkOnTheSameDirectoryDoesNotOverwriteTheFirst() throws Exception {
        writeOneEvent("first");
        writeOneEvent("second");

        assertThat(temp.resolve("events").resolve("events-000001.jsonl.gz")).exists();
        assertThat(temp.resolve("events").resolve("events-000002.jsonl.gz")).exists();
        assertThat(read("events-000001.jsonl.gz")).contains("/first");
        assertThat(read("events-000002.jsonl.gz")).contains("/second");
    }

    @Test
    void concurrentSinksSharingADirectoryEachKeepTheirEvents() throws Exception {
        int writers = 6;
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        CountDownLatch startTogether = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < writers; i++) {
            String path = "writer-" + i;
            futures.add(pool.submit(() -> {
                startTogether.await();
                writeOneEvent(path);
                return null;
            }));
        }
        startTogether.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdownNow();

        StringBuilder everything = new StringBuilder();
        try (Stream<Path> files = Files.list(temp.resolve("events"))) {
            for (Path file : files.toList()) {
                everything.append(read(file.getFileName().toString()));
            }
        }
        for (int i = 0; i < writers; i++) {
            assertThat(everything).as("writer-%d must not be overwritten", i).contains("/writer-" + i);
        }
    }

    private void writeOneEvent(String path) {
        FileCaptureSink sink = new FileCaptureSink(temp, Map.of("serviceName", "demo"), 10, 1024);
        HttpTransaction tx = new HttpTransaction(
                "1", EventType.HTTP_TRANSACTION, Direction.INBOUND, Instant.parse("2026-08-26T22:00:00Z"),
                null, null, "GET", "/" + path, "/" + path, Map.of(), null, "none", "200",
                4, null, null);
        sink.write(new CaptureBatch(List.of(tx), new StatisticsRegistry(10).snapshot()));
        sink.close();
    }

    private String read(String fileName) throws Exception {
        try (GZIPInputStream in = new GZIPInputStream(
                Files.newInputStream(temp.resolve("events").resolve(fileName)))) {
            return new String(in.readAllBytes());
        }
    }
}
