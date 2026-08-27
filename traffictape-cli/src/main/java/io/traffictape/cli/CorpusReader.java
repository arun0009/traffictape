package io.traffictape.cli;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.traffictape.model.EventType;
import io.traffictape.model.HttpTransaction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * Reads events from the sink layout ({@code <dir>/events/*.jsonl[.gz]}), a directory of event
 * files, or a single file — including an {@code aws logs filter-log-events} dump, where
 * non-transaction lines such as {@code STATISTICS} are skipped.
 */
final class CorpusReader {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    record Result(List<HttpTransaction> transactions, int filesRead, int filesTruncated,
                  int linesSkipped, int linesFailed) {
    }

    Result read(Path source) throws IOException {
        List<Path> files = resolveFiles(source);
        if (files.isEmpty()) {
            throw new IOException("No .jsonl or .jsonl.gz event files found under " + source);
        }
        List<HttpTransaction> transactions = new ArrayList<>();
        int truncated = 0;
        int skipped = 0;
        int failed = 0;
        for (Path file : files) {
            // A capture killed mid-flush leaves the last gzip member incomplete. Keep the events
            // that did make it rather than discarding the whole file.
            try (BufferedReader reader = open(file)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    try {
                        // Check the discriminator first so a STATISTICS line in a CloudWatch dump
                        // counts as skipped rather than as a parse failure.
                        JsonNode node = mapper.readTree(trimmed);
                        JsonNode eventType = node.get("eventType");
                        if (eventType == null
                                || !EventType.HTTP_TRANSACTION.name().equals(eventType.asText())) {
                            skipped++;
                            continue;
                        }
                        HttpTransaction tx = mapper.treeToValue(node, HttpTransaction.class);
                        if (tx.method() == null) {
                            skipped++;
                            continue;
                        }
                        transactions.add(tx);
                    } catch (Exception e) {
                        failed++;
                    }
                }
            } catch (IOException e) {
                truncated++;
            }
        }
        return new Result(transactions, files.size(), truncated, skipped, failed);
    }

    private static List<Path> resolveFiles(Path source) throws IOException {
        if (Files.isRegularFile(source)) {
            return List.of(source);
        }
        if (!Files.isDirectory(source)) {
            throw new IOException("Corpus path does not exist: " + source);
        }
        Path events = source.resolve("events");
        Path root = Files.isDirectory(events) ? events : source;
        try (Stream<Path> stream = Files.list(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(CorpusReader::isEventFile)
                    .sorted(Comparator.comparing(Path::getFileName))
                    .toList();
        }
    }

    private static boolean isEventFile(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".jsonl") || name.endsWith(".jsonl.gz");
    }

    private static BufferedReader open(Path file) throws IOException {
        InputStream in = Files.newInputStream(file);
        if (file.getFileName().toString().endsWith(".gz")) {
            in = new GZIPInputStream(in);
        }
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
}
