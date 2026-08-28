package io.traffictape.capture;

import java.net.URI;

/** Canonical outbound destination label: host, plus non-default port. */
public final class DestinationHosts {

    private DestinationHosts() {
    }

    public static String hostPort(URI uri) {
        if (uri == null || uri.getHost() == null) {
            return null;
        }
        String host = uri.getHost();
        int port = uri.getPort();
        if (port <= 0 || isDefaultPort(uri.getScheme(), port)) {
            return host;
        }
        return host + ":" + port;
    }

    static boolean isDefaultPort(String scheme, int port) {
        if (scheme == null) {
            return false;
        }
        return ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
    }
}
