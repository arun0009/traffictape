package io.traffictape.spring.inbound;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Tees the request body as the application reads it. Never pre-consumes the stream
 * and never withholds bytes from the controller. Capture buffer is capped.
 */
public final class BoundedRequestWrapper extends HttpServletRequestWrapper {

    private final int maxBytes;
    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    private boolean truncated;
    private long declaredSize;
    private ServletInputStream stream;

    public BoundedRequestWrapper(HttpServletRequest request, int maxBytes) {
        super(request);
        this.maxBytes = maxBytes;
    }

    public byte[] captured() {
        return captured.toByteArray();
    }

    public boolean truncated() {
        return truncated;
    }

    public long declaredSize() {
        return declaredSize;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (stream == null) {
            stream = new TeeServletInputStream(super.getInputStream());
        }
        return stream;
    }

    private final class TeeServletInputStream extends ServletInputStream {
        private final InputStream delegate;

        private TeeServletInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b >= 0) {
                copy((byte) b);
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = delegate.read(b, off, len);
            if (n > 0) {
                copy(b, off, n);
            }
            return n;
        }

        private void copy(byte b) {
            declaredSize++;
            if (captured.size() < maxBytes) {
                captured.write(b);
            } else {
                truncated = true;
            }
        }

        private void copy(byte[] b, int off, int n) {
            declaredSize += n;
            int room = maxBytes - captured.size();
            if (room > 0) {
                captured.write(b, off, Math.min(n, room));
            }
            if (n > room) {
                truncated = true;
            }
        }

        @Override
        public boolean isFinished() {
            try {
                return delegate.available() == 0;
            } catch (IOException e) {
                return false;
            }
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
        }
    }
}
