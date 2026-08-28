package io.traffictape.spring.outbound;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    /**
     * Copies a prefix of writes while passing every byte through to {@code downstream}.
     * Use this when the body is produced by a writer you must not replace (JAX-RS
     * {@code MessageBodyWriter}, a reactive inserter).
     */
    public static Tee tee(OutputStream downstream, int maxBytes) {
        return new Tee(downstream, maxBytes);
    }

    public static final class Tee extends OutputStream {
        private final OutputStream downstream;
        private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
        private final int maxBytes;
        private long size;
        private boolean truncated;

        private Tee(OutputStream downstream, int maxBytes) {
            this.downstream = downstream == null ? OutputStream.nullOutputStream() : downstream;
            this.maxBytes = Math.max(0, maxBytes);
        }

        @Override
        public void write(int b) throws IOException {
            size++;
            if (captured.size() < maxBytes) {
                captured.write(b);
            } else {
                truncated = true;
            }
            downstream.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (len <= 0) {
                return;
            }
            size += len;
            int room = maxBytes - captured.size();
            if (room > 0) {
                captured.write(b, off, Math.min(len, room));
            }
            if (len > room) {
                truncated = true;
            }
            downstream.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            downstream.flush();
        }

        @Override
        public void close() throws IOException {
            downstream.close();
        }

        public byte[] captured() {
            return captured.toByteArray();
        }

        public boolean truncated() {
            return truncated;
        }

        public long size() {
            return size;
        }
    }
}
