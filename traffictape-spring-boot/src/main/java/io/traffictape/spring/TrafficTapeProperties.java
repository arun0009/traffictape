package io.traffictape.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything under the {@code traffictape.*} prefix. Capture is off unless
 * {@code traffictape.enabled=true}; nothing is installed until then.
 *
 * <p>Nested groups: {@link Flush} (when the writer flushes), {@link Output} (where the
 * corpus is written and when files rotate), {@link Capture} (which traffic is eligible),
 * and {@link Redaction} (what is scrubbed before anything is written).
 */
@ConfigurationProperties(prefix = "traffictape")
public class TrafficTapeProperties {

    /**
     * Master switch. Disabled by default: no filter, queue, worker, or file I/O.
     */
    private boolean enabled = false;
    /** First-N bodies per scenario (not per route). Counts continue after this. */
    private int maxExamplesPerScenario = 10;
    /** captureReady after no new unique scenario for this long. Default 6h. */
    private Duration plateauAfter = Duration.ofHours(6);
    /** Request body prefix kept per exchange. Larger bodies are captured truncated. */
    private int maxRequestBytes = 64 * 1024;
    /** Response body prefix kept per exchange. Larger bodies are captured truncated. */
    private int maxResponseBytes = 64 * 1024;
    /** Hand-off queue depth. When full, exchanges are dropped rather than blocking the request. */
    private int queueSize = 2_000;
    /** Cap on distinct route+scenario keys tracked, bounding memory on high-cardinality traffic. */
    private int maxUniqueFingerprints = 10_000;
    /** How long shutdown waits for the writer to drain before giving up. */
    private Duration shutdownDrain = Duration.ofSeconds(5);
    private final Flush flush = new Flush();
    private final Output output = new Output();
    private final Capture capture = new Capture();
    private final Redaction redaction = new Redaction();
    /** Maps an outbound host[:port] to a service name recorded on the event. */
    private Map<String, String> destinations = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxExamplesPerScenario() {
        return maxExamplesPerScenario;
    }

    public void setMaxExamplesPerScenario(int maxExamplesPerScenario) {
        this.maxExamplesPerScenario = maxExamplesPerScenario;
    }

    public Duration getPlateauAfter() {
        return plateauAfter;
    }

    public void setPlateauAfter(Duration plateauAfter) {
        this.plateauAfter = plateauAfter;
    }

    public int getMaxRequestBytes() {
        return maxRequestBytes;
    }

    public void setMaxRequestBytes(int maxRequestBytes) {
        this.maxRequestBytes = maxRequestBytes;
    }

    public int getMaxResponseBytes() {
        return maxResponseBytes;
    }

    public void setMaxResponseBytes(int maxResponseBytes) {
        this.maxResponseBytes = maxResponseBytes;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    public int getMaxUniqueFingerprints() {
        return maxUniqueFingerprints;
    }

    public void setMaxUniqueFingerprints(int maxUniqueFingerprints) {
        this.maxUniqueFingerprints = maxUniqueFingerprints;
    }

    public Duration getShutdownDrain() {
        return shutdownDrain;
    }

    public void setShutdownDrain(Duration shutdownDrain) {
        this.shutdownDrain = shutdownDrain;
    }

    public Flush getFlush() {
        return flush;
    }

    public Output getOutput() {
        return output;
    }

    public Capture getCapture() {
        return capture;
    }

    public Redaction getRedaction() {
        return redaction;
    }

    public Map<String, String> getDestinations() {
        return destinations;
    }

    public void setDestinations(Map<String, String> destinations) {
        this.destinations = destinations;
    }

    public String destinationName(String host) {
        if (host == null) {
            return null;
        }
        return destinations.getOrDefault(host, host);
    }

    public void validate() {
        if (maxExamplesPerScenario < 0) {
            throw new IllegalArgumentException("traffictape.max-examples-per-scenario must be >= 0");
        }
        if (maxRequestBytes <= 0 || maxResponseBytes <= 0) {
            throw new IllegalArgumentException("traffictape.max-request-bytes and max-response-bytes must be > 0");
        }
        if (queueSize <= 0) {
            throw new IllegalArgumentException("traffictape.queue-size must be > 0");
        }
        if (maxUniqueFingerprints <= 0) {
            throw new IllegalArgumentException("traffictape.max-unique-fingerprints must be > 0");
        }
        if (plateauAfter == null || plateauAfter.isNegative()) {
            throw new IllegalArgumentException("traffictape.plateau-after must be >= 0");
        }
        if (shutdownDrain == null || shutdownDrain.isNegative()) {
            throw new IllegalArgumentException("traffictape.shutdown-drain must be >= 0");
        }
        if (flush.getInterval() == null || flush.getInterval().isNegative() || flush.getInterval().isZero()) {
            throw new IllegalArgumentException("traffictape.flush.interval must be > 0");
        }
        if (flush.getMaxEvents() <= 0 || flush.getMaxBytes() <= 0) {
            throw new IllegalArgumentException("traffictape.flush.max-events and max-bytes must be > 0");
        }
        if (output.getDirectory() == null || output.getDirectory().isBlank()) {
            throw new IllegalArgumentException("traffictape.output.directory must not be blank");
        }
        if (output.getRotateAfterEvents() <= 0 || output.getRotateAfterBytes() <= 0) {
            throw new IllegalArgumentException("traffictape.output.rotate-after-* must be > 0");
        }
    }

    public static class Flush {
        /** Longest a captured exchange waits before the writer hands it to the sink. */
        private Duration interval = Duration.ofSeconds(30);
        /** Flush once this many exchanges are batched, without waiting for the interval. */
        private int maxEvents = 1000;
        /** Flush once the batch reaches this many bytes, without waiting for the interval. */
        private long maxBytes = 50L * 1024 * 1024;

        public Duration getInterval() {
            return interval;
        }

        public void setInterval(Duration interval) {
            this.interval = interval;
        }

        public int getMaxEvents() {
            return maxEvents;
        }

        public void setMaxEvents(int maxEvents) {
            this.maxEvents = maxEvents;
        }

        public long getMaxBytes() {
            return maxBytes;
        }

        public void setMaxBytes(long maxBytes) {
            this.maxBytes = maxBytes;
        }
    }

    public static class Output {
        /** Output directory. Use a disposable path; this is not meant to persist. */
        private String directory = "/tmp/traffic-tape";

        /**
         * When to start a new events file. Independent of {@code flush.*}, which only controls how
         * the writer batches: flushing eagerly should not mean one file per request.
         */
        private int rotateAfterEvents = 1000;
        /** Start a new events file once the current one reaches this many bytes. */
        private long rotateAfterBytes = 50L * 1024 * 1024;

        public String getDirectory() {
            return directory;
        }

        public void setDirectory(String directory) {
            this.directory = directory;
        }

        public int getRotateAfterEvents() {
            return rotateAfterEvents;
        }

        public void setRotateAfterEvents(int rotateAfterEvents) {
            this.rotateAfterEvents = rotateAfterEvents;
        }

        public long getRotateAfterBytes() {
            return rotateAfterBytes;
        }

        public void setRotateAfterBytes(long rotateAfterBytes) {
            this.rotateAfterBytes = rotateAfterBytes;
        }
    }

    public static class Capture {
        private final Include include = new Include();
        private final Exclude exclude = new Exclude();

        /**
         * Capture non-JSON text bodies (XML, form-urlencoded, plain text). XML and form-urlencoded
         * are field-redacted; plain text cannot be. Set to false to omit all of them.
         */
        private boolean textBodies = true;

        public Include getInclude() {
            return include;
        }

        public Exclude getExclude() {
            return exclude;
        }

        public boolean isTextBodies() {
            return textBodies;
        }

        public void setTextBodies(boolean textBodies) {
            this.textBodies = textBodies;
        }
    }

    public static class Include {
        /** Methods to record. Anything else is ignored. */
        private List<String> methods = new ArrayList<>(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        /** Allow-list of headers to store. Empty means all except the redaction denylist. */
        private List<String> headers = new ArrayList<>();
        /** Allow-list of JSON fields to store. Empty means all except the redaction denylist. */
        private List<String> jsonFields = new ArrayList<>();

        public List<String> getMethods() {
            return methods;
        }

        public void setMethods(List<String> methods) {
            this.methods = methods;
        }

        public List<String> getHeaders() {
            return headers;
        }

        public void setHeaders(List<String> headers) {
            this.headers = headers;
        }

        public List<String> getJsonFields() {
            return jsonFields;
        }

        public void setJsonFields(List<String> jsonFields) {
            this.jsonFields = jsonFields;
        }
    }

    public static class Exclude {
        /** Path globs never recorded, in either direction. */
        private List<String> routes = new ArrayList<>(List.of("/health", "/actuator/**"));
        /** Content types whose bodies are never captured. */
        private List<String> contentTypes = new ArrayList<>(List.of("multipart/form-data", "application/octet-stream"));
        /** Outbound host[:port] globs never recorded. */
        private List<String> destinations = new ArrayList<>();

        /**
         * Drops the whole exchange when a request carries one of these headers — synthetic traffic
         * such as a smoke-test harness or an uptime monitor. Values are globs; an empty list or
         * {@code "*"} matches on presence alone. Not to be confused with {@code include.headers},
         * which selects the headers stored on exchanges that are recorded.
         */
        private Map<String, List<String>> requestHeaders = new LinkedHashMap<>();

        public List<String> getRoutes() {
            return routes;
        }

        public void setRoutes(List<String> routes) {
            this.routes = routes;
        }

        public List<String> getContentTypes() {
            return contentTypes;
        }

        public void setContentTypes(List<String> contentTypes) {
            this.contentTypes = contentTypes;
        }

        public List<String> getDestinations() {
            return destinations;
        }

        public void setDestinations(List<String> destinations) {
            this.destinations = destinations;
        }

        public Map<String, List<String>> getRequestHeaders() {
            return requestHeaders;
        }

        public void setRequestHeaders(Map<String, List<String>> requestHeaders) {
            this.requestHeaders = requestHeaders;
        }
    }

    public static class Redaction {
        /** Turning this off writes credentials and cookies as-is. Logs a WARN. */
        private boolean enabled = true;
        /** Headers replaced with [REDACTED]. Setting this replaces the defaults; list them again to keep them. */
        private List<String> headers = new ArrayList<>(List.of(
                "Authorization", "Cookie", "Set-Cookie", "Proxy-Authorization", "X-Api-Key", "Api-Key"));
        /**
         * Names redacted in JSON fields at any depth, XML elements and attributes, and
         * form-urlencoded pairs. Matched case-insensitively on the name only, so a secret in a
         * field that is not listed is stored verbatim. Setting this replaces the defaults.
         */
        private List<String> jsonFields = new ArrayList<>(List.of(
                "password", "token", "accessToken", "refreshToken", "secret",
                "clientSecret", "ssn", "creditCard", "cardNumber", "cvv"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getHeaders() {
            return headers;
        }

        public void setHeaders(List<String> headers) {
            this.headers = headers;
        }

        public List<String> getJsonFields() {
            return jsonFields;
        }

        public void setJsonFields(List<String> jsonFields) {
            this.jsonFields = jsonFields;
        }
    }
}
