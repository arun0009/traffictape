package io.traffictape.capture;

import io.traffictape.model.HttpTransaction;

import java.util.ArrayList;
import java.util.List;

/** Test/benchmark sink. Not for production capture. */
public final class InMemoryCaptureSink implements CaptureSink {

    private final List<HttpTransaction> written = new ArrayList<>();
    private volatile boolean fail;
    private volatile RuntimeException failure = new RuntimeException("sink failed");

    private volatile int remainingFailures;

    public void failWith(RuntimeException e) {
        this.failure = e;
        this.fail = true;
    }

    public void fail() {
        this.fail = true;
    }

    public void failTimes(int n) {
        this.remainingFailures = n;
        this.fail = false;
    }

    public synchronized List<HttpTransaction> written() {
        return List.copyOf(written);
    }

    public synchronized void clear() {
        written.clear();
    }

    @Override
    public synchronized void write(CaptureBatch batch) {
        if (remainingFailures > 0 && batch != null && batch.size() > 0) {
            remainingFailures--;
            throw failure;
        }
        if (fail) {
            throw failure;
        }
        if (batch != null && batch.transactions() != null) {
            written.addAll(batch.transactions());
        }
    }
}
