package io.traffictape.capture;

import io.traffictape.model.HttpTransaction;
import io.traffictape.statistics.StatisticsRegistry;

import java.util.List;

/**
 * A flushable group of corpus events. Sinks must not assume they hold the full capture.
 */
public record CaptureBatch(
        List<HttpTransaction> transactions,
        StatisticsRegistry.Snapshot statistics
) {
    public int size() {
        return transactions == null ? 0 : transactions.size();
    }

    public long approximateBytes() {
        if (transactions == null) {
            return 0;
        }
        long bytes = 0;
        for (HttpTransaction tx : transactions) {
            if (tx.request() != null && tx.request().body() != null) {
                bytes += tx.request().body().capturedBytes();
            }
            if (tx.response() != null && tx.response().body() != null) {
                bytes += tx.response().body().capturedBytes();
            }
        }
        return bytes;
    }
}
