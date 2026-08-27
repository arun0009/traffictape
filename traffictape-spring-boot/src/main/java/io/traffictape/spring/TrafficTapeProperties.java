package io.traffictape.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "traffictape")
public class TrafficTapeProperties {

    /**
     * Master switch. Disabled by default: no filter, queue, worker, or file I/O.
     */
    private boolean enabled = false;
    /** First-N bodies per scenario (not per route). Counts continue after this. */
    private int maxExamplesPerScenario = 50;
    /** captureReady after no new unique scenario for this long. Default 6h. */
    private Duration plateauAfter = Duration.ofHours(6);
    private int maxRequestBytes = 1024 * 1024;
    private int maxResponseBytes = 1024 * 1024;
    private int queueSize = 100_000;
    private int maxUniqueFingerprints = 50_000;
    private Duration shutdownDrain = Duration.ofSeconds(5);
    private final Flush flush = new Flush();
    private final Output output = new Output();
    private final Capture capture = new Capture();
    private final Redaction redaction = new Redaction();
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

    public static class Flush {
        private Duration interval = Duration.ofSeconds(30);
        private int maxEvents = 1000;
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
        private String directory = "/tmp/traffic-tape";
        private String compression = "gzip";

        public String getDirectory() {
            return directory;
        }

        public void setDirectory(String directory) {
            this.directory = directory;
        }

        public String getCompression() {
            return compression;
        }

        public void setCompression(String compression) {
            this.compression = compression;
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
        private List<String> methods = new ArrayList<>(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        private List<String> headers = new ArrayList<>();
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
        private List<String> routes = new ArrayList<>(List.of("/health", "/actuator/**"));
        private List<String> contentTypes = new ArrayList<>(List.of("multipart/form-data", "application/octet-stream"));
        private List<String> destinations = new ArrayList<>();

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
    }

    public static class Redaction {
        private boolean enabled = true;
        private List<String> headers = new ArrayList<>(List.of(
                "Authorization", "Cookie", "Set-Cookie", "Proxy-Authorization", "X-Api-Key", "Api-Key"));
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
