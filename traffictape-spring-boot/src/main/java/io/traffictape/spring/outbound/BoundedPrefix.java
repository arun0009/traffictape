package io.traffictape.spring.outbound;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Arrays;

/** Bounded prefix of an HTTP body, with the remainder still readable. */
public final class BoundedPrefix {

    private final byte[] captured;
    private final boolean truncated;
    private final long declaredSize;
    private final InputStream stream;

    private BoundedPrefix(byte[] captured, boolean truncated, long declaredSize, InputStream stream) {
        this.captured = captured;
        this.truncated = truncated;
        this.declaredSize = declaredSize;
        this.stream = stream;
    }

    public static BoundedPrefix copy(InputStream in, int maxBytes) throws IOException {
        if (in == null) {
            return new BoundedPrefix(new byte[0], false, 0, InputStream.nullInputStream());
        }
        byte[] prefix = in.readNBytes(maxBytes + 1);
        boolean truncated = prefix.length > maxBytes;
        byte[] captured = truncated ? Arrays.copyOf(prefix, maxBytes) : prefix;
        InputStream app = truncated
                ? new SequenceInputStream(new ByteArrayInputStream(captured), in)
                : new ByteArrayInputStream(prefix);
        return new BoundedPrefix(captured, truncated, truncated ? maxBytes + 1L : prefix.length, app);
    }

    public byte[] captured() {
        return captured;
    }

    public boolean truncated() {
        return truncated;
    }

    public long declaredSize() {
        return declaredSize;
    }

    public InputStream stream() {
        return stream;
    }
}
