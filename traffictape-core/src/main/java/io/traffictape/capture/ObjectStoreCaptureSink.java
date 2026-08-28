package io.traffictape.capture;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.traffictape.corpus.CorpusCompanionFiles;
import io.traffictape.model.HttpTransaction;
import io.traffictape.statistics.StatisticsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

/**
 * Same corpus tree as the file sink, via {@link ObjectPutter}. Use this from a {@code @Bean CaptureSink}
 * when you already have object storage; creating the bucket is infra, not this library.
 * Put failures throw; the worker catches them.
 */
public final class ObjectStoreCaptureSink implements CaptureSink {

    private static final Logger log = LoggerFactory.getLogger(ObjectStoreCaptureSink.class);

    @FunctionalInterface
    public interface ObjectPutter {
        void put(String relativePath, byte[] content, String contentType) throws IOException;
    }

    private final ObjectPutter putter;
    private final ObjectMapper mapper;
    private final Map<String, Object> metadataTemplate;
    private final AtomicInteger sequence = new AtomicInteger();
    private final Instant captureStart = Instant.now();

    public ObjectStoreCaptureSink(ObjectPutter putter, Map<String, Object> metadataTemplate) {
        this.putter = putter;
        this.mapper = JsonSupport.mapper();
        this.metadataTemplate = metadataTemplate == null ? Map.of() : metadataTemplate;
    }

    @Override
    public synchronized void write(CaptureBatch batch) {
        if (batch == null || batch.size() == 0) {
            writeIndex(batch == null ? null : batch.statistics());
            return;
        }
        try {
            putter.put(
                    "events/events-%06d.jsonl.gz".formatted(sequence.incrementAndGet()),
                    gzipJsonl(batch),
                    "application/gzip");
            writeIndex(batch.statistics());
        } catch (IOException e) {
            log.debug("TrafficTape object-store write failed", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized void flush() {
        writeMetadata(null);
    }

    @Override
    public synchronized void close() {
        writeMetadata(null);
    }

    private byte[] gzipJsonl(CaptureBatch batch) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            for (HttpTransaction tx : batch.transactions()) {
                gzip.write(mapper.writeValueAsBytes(tx));
                gzip.write('\n');
            }
        }
        return out.toByteArray();
    }

    private void writeIndex(StatisticsRegistry.Snapshot snapshot) {
        try {
            CorpusCompanionFiles.writeStatistics(snapshot, mapper, putter::put);
            writeMetadata(snapshot);
            CorpusCompanionFiles.writeSidecars(snapshot, mapper, putter::put);
        } catch (IOException e) {
            log.debug("TrafficTape index put failed", e);
        }
    }

    private void writeMetadata(StatisticsRegistry.Snapshot snapshot) {
        try {
            putter.put(
                    CorpusCompanionFiles.METADATA,
                    CorpusCompanionFiles.pretty(mapper, CorpusCompanionFiles.metadata(metadataTemplate, captureStart, snapshot)),
                    "application/json");
        } catch (IOException e) {
            log.debug("TrafficTape metadata.json put failed", e);
        }
    }
}
