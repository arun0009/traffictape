package io.traffictape.sink.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.traffictape.capture.CaptureBatch;
import io.traffictape.capture.CaptureSink;
import io.traffictape.capture.JsonSupport;
import io.traffictape.tape.TapeFiles;
import io.traffictape.model.HttpTransaction;
import io.traffictape.statistics.StatisticsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/**
 * Writes:
 * <pre>
 *   {directory}/
 *     README.md
 *     metadata.json
 *     statistics.json
 *     gaps.json
 *     fanout.json
 *     events/events-NNNNNN.jsonl.gz
 * </pre>
 */
public final class FileCaptureSink implements CaptureSink {

    private static final Logger log = LoggerFactory.getLogger(FileCaptureSink.class);
    private static final Pattern EVENT_FILE = Pattern.compile("events-(\\d+)\\.jsonl\\.gz");

    private final Path directory;
    private final ObjectMapper mapper;
    private final Map<String, Object> metadataTemplate;
    private final AtomicInteger sequence = new AtomicInteger();
    private final Instant captureStart = Instant.now();
    private OutputStream currentOut;
    private int eventsInFile;
    private long bytesInFile;
    private final int rotateAfterEvents;
    private final long rotateAfterBytes;

    private final boolean disabled;

    public FileCaptureSink(Path directory, Map<String, Object> metadataTemplate) {
        this(directory, metadataTemplate, 1000, 50L * 1024 * 1024);
    }

    public FileCaptureSink(Path directory, Map<String, Object> metadataTemplate, int rotateAfterEvents, long rotateAfterBytes) {
        this.directory = directory;
        this.mapper = JsonSupport.mapper();
        this.metadataTemplate = metadataTemplate == null ? Map.of() : metadataTemplate;
        this.rotateAfterEvents = rotateAfterEvents;
        this.rotateAfterBytes = rotateAfterBytes;
        boolean ok = false;
        try {
            Files.createDirectories(directory.resolve("events"));
            sequence.set(highestExistingSequence(directory.resolve("events")));
            writeIndex(null);
            ok = true;
        } catch (IOException e) {
            log.error("TrafficTape file sink disabled; cannot write {}", directory, e);
        }
        this.disabled = !ok;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    /**
     * Resumes numbering after any events already in the directory. Without this, a second sink on
     * the same directory — an application restart, or another Spring context in the same test JVM —
     * would start again at {@code events-000001} and overwrite the earlier run.
     */
    private static int highestExistingSequence(Path events) throws IOException {
        int highest = 0;
        try (java.util.stream.Stream<Path> files = Files.list(events)) {
            for (Path file : files.toList()) {
                Matcher matcher = EVENT_FILE.matcher(file.getFileName().toString());
                if (matcher.matches()) {
                    highest = Math.max(highest, Integer.parseInt(matcher.group(1)));
                }
            }
        }
        return highest;
    }

    @Override
    public synchronized void write(CaptureBatch batch) {
        if (disabled) {
            return;
        }
        if (batch == null || batch.size() == 0) {
            writeIndex(batch == null ? null : batch.statistics());
            return;
        }
        try {
            ensureStream();
            for (HttpTransaction tx : batch.transactions()) {
                byte[] line = mapper.writeValueAsBytes(tx);
                currentOut.write(line);
                currentOut.write('\n');
                eventsInFile++;
                bytesInFile += line.length + 1;
                if (eventsInFile >= rotateAfterEvents || bytesInFile >= rotateAfterBytes) {
                    rotate();
                    ensureStream();
                }
            }
            currentOut.flush();
            writeIndex(batch.statistics());
        } catch (IOException e) {
            log.debug("TrafficTape file sink write failed", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized void flush() {
        try {
            if (currentOut != null) {
                currentOut.flush();
            }
            writeMetadata(null);
        } catch (IOException e) {
            log.debug("TrafficTape file sink flush failed", e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            if (currentOut != null) {
                currentOut.close();
                currentOut = null;
            }
            writeMetadata(null);
        } catch (IOException e) {
            log.debug("TrafficTape file sink close failed", e);
        }
    }

    public Path directory() {
        return directory;
    }

    private void ensureStream() throws IOException {
        if (currentOut != null) {
            return;
        }
        // CREATE_NEW rather than truncate: two JVMs sharing a directory (parallel Surefire forks,
        // or a rolling restart on a shared volume) can pick the same number, and the loser must
        // take the next one instead of erasing the winner's events.
        OutputStream raw = null;
        while (raw == null) {
            int n = sequence.incrementAndGet();
            Path file = directory.resolve("events").resolve("events-%06d.jsonl.gz".formatted(n));
            try {
                raw = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (FileAlreadyExistsException taken) {
                log.debug("TrafficTape events file {} already exists; trying the next number", file);
            }
        }
        currentOut = new GZIPOutputStream(new BufferedOutputStream(raw, 64 * 1024));
        eventsInFile = 0;
        bytesInFile = 0;
    }

    private void rotate() throws IOException {
        if (currentOut != null) {
            currentOut.close();
            currentOut = null;
        }
    }

    private void writeIndex(StatisticsRegistry.Snapshot snapshot) {
        try {
            TapeFiles.Writer writer = (path, bytes, type) -> atomicWrite(directory.resolve(path), bytes);
            TapeFiles.writeStatistics(snapshot, mapper, writer);
            writeMetadata(snapshot);
            TapeFiles.writeSidecars(snapshot, mapper, writer);
        } catch (IOException e) {
            log.debug("TrafficTape index write failed", e);
        }
    }

    private void writeMetadata(StatisticsRegistry.Snapshot snapshot) throws IOException {
        atomicWrite(
                directory.resolve(TapeFiles.METADATA),
                TapeFiles.pretty(mapper, TapeFiles.metadata(metadataTemplate, captureStart, snapshot)));
    }

    private static void atomicWrite(Path target, byte[] bytes) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, bytes);
        try {
            Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
