package io.traffictape.capture;

import io.traffictape.model.HttpTransaction;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bounded, non-blocking queue. {@link #offer} never waits.
 */
public final class CaptureQueue {

    private final ArrayBlockingQueue<HttpTransaction> queue;
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public CaptureQueue(int capacity) {
        this.queue = new ArrayBlockingQueue<>(Math.max(1, capacity));
    }

    public boolean offer(HttpTransaction transaction) {
        if (!accepting.get() || transaction == null) {
            return false;
        }
        return queue.offer(transaction);
    }

    public HttpTransaction poll(Duration timeout) throws InterruptedException {
        return queue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public List<HttpTransaction> drain(int max) {
        List<HttpTransaction> batch = new ArrayList<>(Math.min(max, queue.size()));
        queue.drainTo(batch, max);
        return batch;
    }

    public int size() {
        return queue.size();
    }

    public void stopAccepting() {
        accepting.set(false);
    }
}
