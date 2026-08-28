package io.traffictape.sink.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.traffictape.capture.CaptureBatch;
import io.traffictape.capture.CaptureSink;
import io.traffictape.capture.JsonSupport;
import io.traffictape.model.HttpTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One JSON object per line on logger {@value #LOGGER_NAME}. Route that logger with your log driver;
 * this class does not create infrastructure.
 */
public final class LoggingCaptureSink implements CaptureSink {

    public static final String LOGGER_NAME = "traffictape.tape";

    @FunctionalInterface
    interface LineWriter {
        void write(String jsonLine);
    }

    private final LineWriter writer;
    private final ObjectMapper mapper;

    public LoggingCaptureSink() {
        this(LoggerFactory.getLogger(LOGGER_NAME)::info);
    }

    LoggingCaptureSink(LineWriter writer) {
        this.writer = writer;
        this.mapper = JsonSupport.mapper();
    }

    @Override
    public void write(CaptureBatch batch) {
        if (batch == null || batch.size() == 0) {
            return;
        }
        for (HttpTransaction tx : batch.transactions()) {
            try {
                writer.write(mapper.writeValueAsString(tx));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
