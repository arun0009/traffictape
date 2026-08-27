package io.traffictape.capture;

import io.traffictape.body.BodyCodec;
import io.traffictape.correlation.ExchangeContext;
import io.traffictape.fingerprint.DefaultFingerprinter;
import io.traffictape.fingerprint.Fingerprinter;
import io.traffictape.fingerprint.JsonShapeExtractor;
import io.traffictape.fingerprint.PathNormalizer;
import io.traffictape.model.Direction;
import io.traffictape.model.FingerprintPair;
import io.traffictape.model.HttpTransaction;
import io.traffictape.policy.CapturePolicy;
import io.traffictape.redaction.Redactor;
import io.traffictape.sampling.BoundedScenarioSampler;
import io.traffictape.sampling.Sampler;
import io.traffictape.sampling.ScenarioKey;
import io.traffictape.statistics.StatisticsRegistry;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The capture engine. Adapters only call {@link #record}; backends only implement {@link CaptureSink}.
 *
 * <pre>
 *   adapter  →  ObservedExchange  →  record()  →  stats always
 *                                      └─ sampler? → redact → queue.offer
 *                                                            → worker → CaptureSink
 * </pre>
 *
 * Fail-open: {@code record} never throws to the application.
 */
public final class CaptureEngine {

    private static final Logger log = LoggerFactory.getLogger(CaptureEngine.class);

    private final CapturePolicy policy;
    private final PathNormalizer pathNormalizer;
    private final Fingerprinter fingerprinter;
    private final JsonShapeExtractor shapeExtractor;
    private final Sampler sampler;
    private final StatisticsRegistry statistics;
    private final HttpTransactionFactory transactions;
    private final CaptureQueue queue;
    private final CaptureMetrics metrics;

    CaptureEngine(Builder builder) {
        this.policy = builder.policy;
        this.pathNormalizer = builder.pathNormalizer;
        this.fingerprinter = builder.fingerprinter;
        this.shapeExtractor = builder.shapeExtractor;
        this.sampler = builder.sampler;
        this.statistics = builder.statistics;
        this.transactions = new HttpTransactionFactory(builder.bodyCodec, builder.redactor);
        this.queue = builder.queue;
        this.metrics = builder.metrics == null ? CaptureMetrics.NOOP : builder.metrics;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CaptureEngine createDefault(CaptureQueue queue, int maxExamplesPerScenario) {
        return builder().queue(queue).maxExamplesPerScenario(maxExamplesPerScenario).build();
    }

    public CapturePolicy policy() {
        return policy;
    }

    public StatisticsRegistry statistics() {
        return statistics;
    }

    public CaptureQueue queue() {
        return queue;
    }

    public Sampler sampler() {
        return sampler;
    }

    public PathNormalizer pathNormalizer() {
        return pathNormalizer;
    }

    /**
     * Observe one HTTP exchange. Always updates statistics. Enqueues a corpus example only
     * when the sampler still wants this scenario. Never throws.
     */
    public void record(ObservedExchange observed) {
        long start = System.nanoTime();
        try {
            if (!policy.accepts(observed)) {
                return;
            }
            String path = observed.path() == null ? "/" : observed.path();
            String route = pathNormalizer.preferTemplate(observed.route(), path);
            String requestShape = requestShape(observed);
            boolean emptyResponse = observed.responseBody().length == 0;
            String responseCharacteristic = ScenarioKey.responseCharacteristic(observed.status(), emptyResponse);
            FingerprintPair pair = fingerprinter.pair(
                    observed.direction(),
                    observed.method(),
                    route,
                    observed.query(),
                    requestShape,
                    responseCharacteristic);
            ScenarioKey key = new ScenarioKey(pair.endpoint().id(), requestShape, responseCharacteristic);
            statistics.recordObservation(
                    observed.direction(),
                    observed.method(),
                    route,
                    pair.endpoint(),
                    pair.scenario(),
                    observed.status(),
                    observed.latencyMs(),
                    observed.requestSize(),
                    observed.responseSize(),
                    observed.timestamp());
            recordFanout(observed, pair, route);
            metrics.recordObserved(observed.direction().name());
            metrics.recordFingerprints(statistics.uniqueEndpoints(), statistics.uniqueScenarios());

            if (!sampler.shouldCapture(key)) {
                return;
            }
            HttpTransaction tx = transactions.create(observed, route, requestShape, responseCharacteristic, pair);
            if (queue.offer(tx)) {
                sampler.recordCaptured(key);
                long bytes = bytesOf(tx);
                statistics.recordCaptured(pair.scenario(), bytes);
                metrics.recordExampleCaptured();
                metrics.recordBytes(bytes);
            } else {
                statistics.recordDropped();
                metrics.recordDropped();
            }
            metrics.recordQueueSize(queue.size());
        } catch (Throwable t) {
            metrics.recordError();
            log.debug("TrafficTape capture failed; application request continues", t);
        } finally {
            metrics.recordCaptureLatencyNanos(System.nanoTime() - start);
        }
    }

    public boolean offer(HttpTransaction transaction) {
        try {
            if (!queue.offer(transaction)) {
                statistics.recordDropped();
                metrics.recordDropped();
                return false;
            }
            return true;
        } catch (Throwable t) {
            metrics.recordError();
            return false;
        }
    }

    private void recordFanout(ObservedExchange observed, FingerprintPair pair, String route) {
        ExchangeContext ctx = observed.exchangeContext();
        if (ctx == null || ctx.exchangeId() == null || ctx.exchangeId().isBlank()) {
            return;
        }
        if (observed.direction() == Direction.OUTBOUND) {
            statistics.recordFanoutHop(
                    ctx.exchangeId(),
                    observed.outboundSequence(),
                    observed.destination(),
                    observed.method(),
                    route,
                    observed.status());
        } else if (observed.direction() == Direction.INBOUND) {
            statistics.completeFanout(
                    ctx.exchangeId(),
                    pair.scenario().id(),
                    pair.scenario().label(),
                    observed.method(),
                    route);
        }
    }

    private String requestShape(ObservedExchange observed) {
        try {
            return shapeExtractor.extract(observed.requestBody(), observed.requestContentType());
        } catch (Throwable t) {
            return "unparsed";
        }
    }

    private static long bytesOf(HttpTransaction tx) {
        long n = 0;
        if (tx.request() != null && tx.request().body() != null) {
            n += tx.request().body().capturedBytes();
        }
        if (tx.response() != null && tx.response().body() != null) {
            n += tx.response().body().capturedBytes();
        }
        return n;
    }

    /**
     * Wire collaborators. Unset fields get safe defaults so tests and adapters stay small.
     */
    public static final class Builder {
        private CapturePolicy policy = CapturePolicy.safeDefaults();
        private PathNormalizer pathNormalizer = new PathNormalizer();
        private Fingerprinter fingerprinter = new DefaultFingerprinter();
        private JsonShapeExtractor shapeExtractor;
        private Sampler sampler;
        private StatisticsRegistry statistics;
        private BodyCodec bodyCodec;
        private Redactor redactor;
        private CaptureQueue queue;
        private CaptureMetrics metrics = CaptureMetrics.NOOP;
        private int maxExamplesPerScenario = 50;
        private int maxUniqueFingerprints = 50_000;
        private int maxBodyBytes = 1024 * 1024;
        private Duration plateauAfter = StatisticsRegistry.DEFAULT_PLATEAU_AFTER;

        public Builder policy(CapturePolicy policy) {
            this.policy = policy;
            return this;
        }

        public Builder pathNormalizer(PathNormalizer pathNormalizer) {
            this.pathNormalizer = pathNormalizer;
            return this;
        }

        public Builder fingerprinter(Fingerprinter fingerprinter) {
            this.fingerprinter = fingerprinter;
            return this;
        }

        public Builder shapeExtractor(JsonShapeExtractor shapeExtractor) {
            this.shapeExtractor = shapeExtractor;
            return this;
        }

        public Builder sampler(Sampler sampler) {
            this.sampler = sampler;
            return this;
        }

        public Builder statistics(StatisticsRegistry statistics) {
            this.statistics = statistics;
            return this;
        }

        public Builder bodyCodec(BodyCodec bodyCodec) {
            this.bodyCodec = bodyCodec;
            return this;
        }

        public Builder redactor(Redactor redactor) {
            this.redactor = redactor;
            return this;
        }

        public Builder queue(CaptureQueue queue) {
            this.queue = queue;
            return this;
        }

        public Builder metrics(CaptureMetrics metrics) {
            this.metrics = metrics;
            return this;
        }

        public Builder maxExamplesPerScenario(int maxExamplesPerScenario) {
            this.maxExamplesPerScenario = maxExamplesPerScenario;
            return this;
        }

        public Builder plateauAfter(Duration plateauAfter) {
            this.plateauAfter = plateauAfter;
            return this;
        }

        public CaptureEngine build() {
            if (queue == null) {
                queue = new CaptureQueue(10_000);
            }
            if (sampler == null) {
                sampler = new BoundedScenarioSampler(maxExamplesPerScenario);
            }
            if (statistics == null) {
                statistics = new StatisticsRegistry(maxUniqueFingerprints, maxExamplesPerScenario, plateauAfter);
            }
            if (redactor == null) {
                redactor = new Redactor(policy);
            }
            var mapper = JsonSupport.mapper();
            if (shapeExtractor == null) {
                shapeExtractor = new JsonShapeExtractor(mapper);
            }
            if (bodyCodec == null) {
                bodyCodec = new BodyCodec(mapper, redactor, maxBodyBytes);
            }
            return new CaptureEngine(this);
        }
    }
}
