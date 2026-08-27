package io.traffictape.policy;

import io.traffictape.capture.ObservedExchange;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Allow/deny policy. Safe default: omit rather than capture.
 */
public final class CapturePolicy {

    private final Set<String> includeMethods;
    private final List<String> excludeRoutes;
    private final List<String> excludeContentTypes;
    private final List<String> excludeDestinations;
    private final Set<String> excludeHeaders;
    private final Set<String> includeHeaders;
    private final Set<String> excludeJsonFields;
    private final Set<String> includeJsonFields;

    private CapturePolicy(Builder builder) {
        this.includeMethods = upper(builder.includeMethods);
        this.excludeRoutes = List.copyOf(builder.excludeRoutes);
        this.excludeContentTypes = lower(builder.excludeContentTypes);
        this.excludeDestinations = lower(builder.excludeDestinations);
        this.excludeHeaders = lowerSet(builder.excludeHeaders);
        this.includeHeaders = lowerSet(builder.includeHeaders);
        this.excludeJsonFields = lowerSet(builder.excludeJsonFields);
        this.includeJsonFields = lowerSet(builder.includeJsonFields);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CapturePolicy safeDefaults() {
        return builder()
                .includeMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"))
                .excludeRoutes(List.of("/health", "/actuator/**"))
                .excludeContentTypes(List.of("multipart/form-data", "application/octet-stream"))
                .excludeHeaders(List.of(
                        "authorization", "cookie", "set-cookie", "proxy-authorization",
                        "x-api-key", "api-key"))
                .excludeJsonFields(List.of(
                        "password", "token", "accesstoken", "refreshtoken", "secret",
                        "clientsecret", "ssn", "creditcard", "cardnumber", "cvv"))
                .build();
    }

    public boolean accepts(ObservedExchange observed) {
        if (observed == null || observed.method() == null) {
            return false;
        }
        String path = observed.path() == null ? "/" : observed.path();
        return acceptsMethod(observed.method())
                && acceptsRoute(path)
                && acceptsDestination(observed.destination());
    }

    public boolean acceptsMethod(String method) {
        if (includeMethods.isEmpty()) {
            return true;
        }
        return method != null && includeMethods.contains(method.toUpperCase(Locale.ROOT));
    }

    public boolean acceptsRoute(String path) {
        if (path == null) {
            return true;
        }
        for (String pattern : excludeRoutes) {
            if (PathGlob.matches(pattern, path)) {
                return false;
            }
        }
        return true;
    }

    public boolean acceptsDestination(String destination) {
        if (destination == null || excludeDestinations.isEmpty()) {
            return true;
        }
        String d = destination.toLowerCase(Locale.ROOT);
        for (String pattern : excludeDestinations) {
            if (d.equals(pattern) || PathGlob.matches(pattern, d)) {
                return false;
            }
        }
        return true;
    }

    public boolean acceptsContentType(String contentType) {
        if (contentType == null || excludeContentTypes.isEmpty()) {
            return true;
        }
        String ct = contentType.toLowerCase(Locale.ROOT);
        int semi = ct.indexOf(';');
        if (semi > 0) {
            ct = ct.substring(0, semi).trim();
        }
        for (String excluded : excludeContentTypes) {
            if (ct.equals(excluded) || ct.startsWith(excluded)) {
                return false;
            }
        }
        return true;
    }

    public boolean captureHeader(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        if (excludeHeaders.contains(n)) {
            return false;
        }
        if (!includeHeaders.isEmpty()) {
            return includeHeaders.contains(n);
        }
        return true;
    }

    public boolean redactJsonField(String name) {
        if (name == null) {
            return false;
        }
        return excludeJsonFields.contains(name.toLowerCase(Locale.ROOT));
    }

    public boolean keepJsonField(String name) {
        if (includeJsonFields.isEmpty()) {
            return true;
        }
        return name != null && includeJsonFields.contains(name.toLowerCase(Locale.ROOT));
    }

    public Set<String> excludeJsonFields() {
        return excludeJsonFields;
    }

    public Set<String> includeJsonFields() {
        return includeJsonFields;
    }

    public Set<String> excludeHeaders() {
        return excludeHeaders;
    }

    private static Set<String> upper(Collection<String> in) {
        Set<String> out = new TreeSet<>();
        for (String s : in) {
            if (s != null) {
                out.add(s.toUpperCase(Locale.ROOT));
            }
        }
        return Set.copyOf(out);
    }

    private static List<String> lower(Collection<String> in) {
        List<String> out = new ArrayList<>();
        for (String s : in) {
            if (s != null) {
                out.add(s.toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(out);
    }

    private static Set<String> lowerSet(Collection<String> in) {
        Set<String> out = new TreeSet<>();
        for (String s : in) {
            if (s != null) {
                out.add(s.toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(out);
    }

    public static final class Builder {
        private Collection<String> includeMethods = List.of();
        private Collection<String> excludeRoutes = List.of();
        private Collection<String> excludeContentTypes = List.of();
        private Collection<String> excludeDestinations = List.of();
        private Collection<String> excludeHeaders = List.of();
        private Collection<String> includeHeaders = List.of();
        private Collection<String> excludeJsonFields = List.of();
        private Collection<String> includeJsonFields = List.of();

        public Builder includeMethods(Collection<String> v) {
            this.includeMethods = v;
            return this;
        }

        public Builder excludeRoutes(Collection<String> v) {
            this.excludeRoutes = v;
            return this;
        }

        public Builder excludeContentTypes(Collection<String> v) {
            this.excludeContentTypes = v;
            return this;
        }

        public Builder excludeDestinations(Collection<String> v) {
            this.excludeDestinations = v;
            return this;
        }

        public Builder excludeHeaders(Collection<String> v) {
            this.excludeHeaders = v;
            return this;
        }

        public Builder includeHeaders(Collection<String> v) {
            this.includeHeaders = v;
            return this;
        }

        public Builder excludeJsonFields(Collection<String> v) {
            this.excludeJsonFields = v;
            return this;
        }

        public Builder includeJsonFields(Collection<String> v) {
            this.includeJsonFields = v;
            return this;
        }

        public CapturePolicy build() {
            return new CapturePolicy(this);
        }
    }
}
