package io.traffictape.capture;

import io.traffictape.correlation.ExchangeContext;
import io.traffictape.model.Direction;
import io.traffictape.model.HttpTransaction;
import io.traffictape.sampling.Sampler;
import io.traffictape.sampling.ScenarioKey;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CaptureEngineTest {

    @Test
    void recordsStatsAndExamples() {
        CaptureQueue queue = new CaptureQueue(10);
        CaptureEngine engine = CaptureEngine.createDefault(queue, 2);
        engine.record(get("/accounts/1", 200));
        engine.record(get("/accounts/2", 200));
        engine.record(get("/accounts/3", 200));
        assertThat(engine.statistics().observed()).isEqualTo(3);
        assertThat(engine.statistics().captured()).isEqualTo(2);
        assertThat(queue.size()).isEqualTo(2);
        HttpTransaction tx = queue.drain(1).get(0);
        assertThat(tx.direction()).isEqualTo(Direction.INBOUND);
        assertThat(tx.route()).isEqualTo("/accounts/{id}");
        assertThat(tx.fingerprints().endpoint().id()).isEqualTo(recordAndFingerprint());
    }

    private String recordAndFingerprint() {
        CaptureQueue q = new CaptureQueue(10);
        CaptureEngine e = CaptureEngine.createDefault(q, 2);
        e.record(get("/accounts/9", 200));
        return q.drain(1).get(0).fingerprints().endpoint().id();
    }

    @Test
    void differentStatusesAreDifferentScenarios() {
        CaptureQueue queue = new CaptureQueue(10);
        CaptureEngine engine = CaptureEngine.createDefault(queue, 1);
        engine.record(get("/accounts/1", 200));
        engine.record(get("/accounts/2", 200));
        engine.record(get("/accounts/3", 404));
        assertThat(engine.statistics().captured()).isEqualTo(2);
        assertThat(queue.size()).isEqualTo(2);
    }

    @Test
    void dropsWhenQueueFull() {
        CaptureQueue queue = new CaptureQueue(1);
        CaptureEngine engine = CaptureEngine.createDefault(queue, 100);
        engine.record(get("/accounts/1", 200));
        engine.record(get("/accounts/2", 200));
        assertThat(queue.size()).isEqualTo(1);
        assertThat(engine.statistics().dropped()).isEqualTo(1);
        assertThat(engine.statistics().observed()).isEqualTo(2);
    }

    @Test
    void skipsHealth() {
        CaptureQueue queue = new CaptureQueue(10);
        CaptureEngine engine = CaptureEngine.createDefault(queue, 10);
        engine.record(ObservedExchange.builder()
                .direction(Direction.INBOUND)
                .timestamp(Instant.now())
                .method("GET")
                .path("/health")
                .status(200)
                .query(Map.of())
                .build());
        assertThat(engine.statistics().observed()).isZero();
        assertThat(queue.size()).isZero();
    }

    @Test
    void skipsMultipartAndOctetStream() {
        CaptureQueue queue = new CaptureQueue(10);
        CaptureEngine engine = CaptureEngine.createDefault(queue, 10);
        engine.record(ObservedExchange.builder()
                .direction(Direction.INBOUND)
                .timestamp(Instant.now())
                .method("POST")
                .path("/uploads")
                .requestContentType("multipart/form-data")
                .status(200)
                .query(Map.of())
                .build());
        engine.record(ObservedExchange.builder()
                .direction(Direction.INBOUND)
                .timestamp(Instant.now())
                .method("GET")
                .path("/blob")
                .responseContentType("application/octet-stream")
                .status(200)
                .query(Map.of())
                .build());
        assertThat(engine.statistics().observed()).isZero();
        assertThat(queue.size()).isZero();
    }

    @Test
    void requestShapesSplitPatchScenarios() {
        CaptureQueue queue = new CaptureQueue(10);
        CaptureEngine engine = CaptureEngine.createDefault(queue, 10);
        engine.record(patch("/assets/1", "{\"status\":\"ACTIVE\"}"));
        engine.record(patch("/assets/2", "{\"owner\":\"team-a\"}"));
        assertThat(queue.size()).isEqualTo(2);
        var events = queue.drain(2);
        assertThat(events.get(0).scenarioFingerprintId()).isNotEqualTo(events.get(1).scenarioFingerprintId());
        assertThat(events.get(0).endpointFingerprintId()).isEqualTo(events.get(1).endpointFingerprintId());
    }

    @Test
    void concurrentRecordsDoNotThrow() throws Exception {
        CaptureQueue queue = new CaptureQueue(1000);
        CaptureEngine engine = CaptureEngine.createDefault(queue, 10);
        Thread[] threads = new Thread[8];
        for (int t = 0; t < threads.length; t++) {
            threads[t] = new Thread(() -> {
                for (int i = 0; i < 50; i++) {
                    engine.record(get("/accounts/" + i, i % 7 == 0 ? 404 : 200));
                }
            });
            threads[t].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        assertThat(engine.statistics().observed()).isEqualTo(400);
    }

    @Test
    void offerNeverThrows() {
        CaptureQueue queue = new CaptureQueue(1);
        CaptureEngine engine = CaptureEngine.createDefault(queue, 10);
        assertThat(engine.offer(null)).isFalse();
    }

    @Test
    void fanoutRecordsOutboundSequenceOnInboundComplete() {
        CaptureQueue queue = new CaptureQueue(10);
        CaptureEngine engine = CaptureEngine.createDefault(queue, 10);
        ExchangeContext ctx = ExchangeContext.open(Map.of());
        engine.record(outbound(ctx, 1, "inventory", "GET", "/stock/{sku}", 200));
        engine.record(outbound(ctx, 2, "ledger", "POST", "/entries", 201));
        engine.record(inbound(ctx, "POST", "/orders", 201));

        var fanout = engine.statistics().snapshot().fanout();
        assertThat(fanout).hasSize(1);
        assertThat(fanout.get(0).method()).isEqualTo("POST");
        assertThat(fanout.get(0).route()).isEqualTo("/orders");
        assertThat(fanout.get(0).observed()).isEqualTo(1);
        assertThat(fanout.get(0).patterns()).hasSize(1);
        assertThat(fanout.get(0).patterns().get(0).hops())
                .contains("GET /stock/{sku} 200 → inventory")
                .contains("POST /entries 201 → ledger");
    }

    @Test
    void customSamplerCanSuppressExamples() {
        CaptureQueue queue = new CaptureQueue(10);
        Sampler never = new Sampler() {
            @Override
            public boolean shouldCapture(ScenarioKey key) {
                return false;
            }

            @Override
            public void recordCaptured(ScenarioKey key) {
            }
        };
        CaptureEngine engine = CaptureEngine.builder().queue(queue).sampler(never).build();
        engine.record(get("/accounts/1", 200));
        assertThat(engine.statistics().observed()).isEqualTo(1);
        assertThat(queue.size()).isZero();
    }

    private static ObservedExchange get(String path, int status) {
        return http(Direction.INBOUND, "GET", path, status)
                .responseBody("{\"ok\":true}".getBytes())
                .responseContentType("application/json")
                .build();
    }

    private static ObservedExchange patch(String path, String json) {
        return http(Direction.INBOUND, "PATCH", path, 200)
                .requestContentType("application/json")
                .requestBody(json.getBytes())
                .responseContentType("application/json")
                .responseBody("{}".getBytes())
                .build();
    }

    private static ObservedExchange inbound(ExchangeContext ctx, String method, String path, int status) {
        return http(Direction.INBOUND, method, path, status)
                .responseBody("{\"id\":\"9\"}".getBytes())
                .responseContentType("application/json")
                .exchangeContext(ctx)
                .build();
    }

    private static ObservedExchange outbound(
            ExchangeContext ctx, int sequence, String destination, String method, String path, int status) {
        return http(Direction.OUTBOUND, method, path, status)
                .destination(destination)
                .responseBody("{}".getBytes())
                .responseContentType("application/json")
                .exchangeContext(ctx)
                .outboundSequence(sequence)
                .build();
    }

    private static ObservedExchange.Builder http(Direction direction, String method, String path, int status) {
        return ObservedExchange.builder()
                .direction(direction)
                .timestamp(Instant.now())
                .method(method)
                .path(path)
                .status(status)
                .query(Map.of());
    }
}
