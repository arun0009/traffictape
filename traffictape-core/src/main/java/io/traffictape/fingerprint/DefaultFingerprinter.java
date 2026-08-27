package io.traffictape.fingerprint;

import io.traffictape.model.Direction;
import io.traffictape.model.Fingerprint;

import java.util.List;
import java.util.Map;

public final class DefaultFingerprinter implements Fingerprinter {

    @Override
    public Fingerprint endpoint(Direction direction, String method, String route, Map<String, List<String>> query) {
        String dir = direction == null ? "UNKNOWN" : direction.name();
        String m = method == null ? "" : method.toUpperCase();
        String r = route == null || route.isBlank() ? "/" : route;
        String q = Fingerprinter.queryShape(query);
        String canonical = dir + "\n" + m + "\n" + r + "\n" + q;
        String label = dir + " " + m + " " + r + (q.isEmpty() ? "" : " q={" + q + "}");
        return new Fingerprint(Fingerprinter.hash(canonical), label);
    }

    @Override
    public Fingerprint scenario(Fingerprint endpoint, String requestShape, String responseCharacteristic) {
        String shape = requestShape == null || requestShape.isBlank() ? JsonShapeExtractor.NONE : requestShape;
        String response = responseCharacteristic == null ? "" : responseCharacteristic;
        String canonical = endpoint.id() + "\n" + shape + "\n" + response;
        String label = endpoint.label() + " shape=" + shape + " resp=" + response;
        return new Fingerprint(Fingerprinter.hash(canonical), label);
    }
}
