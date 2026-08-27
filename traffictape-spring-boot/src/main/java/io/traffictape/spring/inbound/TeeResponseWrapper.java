package io.traffictape.spring.inbound;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;

/**
 * Writes through to the client immediately while copying a capped prefix for capture.
 * Unlike Spring's ContentCachingResponseWrapper this does not delay the response.
 */
public final class TeeResponseWrapper extends HttpServletResponseWrapper {

    private final int maxBytes;
    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    private boolean truncated;
    private long declaredSize;
    private ServletOutputStream stream;
    private PrintWriter writer;
    private boolean buffering = true;

    public TeeResponseWrapper(HttpServletResponse response, int maxBytes) {
        super(response);
        this.maxBytes = maxBytes;
    }

    public void stopBuffering() {
        this.buffering = false;
        captured.reset();
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
    public ServletOutputStream getOutputStream() throws IOException {
        if (writer != null) {
            throw new IllegalStateException("getWriter already called");
        }
        if (stream == null) {
            stream = new TeeServletOutputStream(super.getOutputStream());
        }
        return stream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (stream != null) {
            throw new IllegalStateException("getOutputStream already called");
        }
        if (writer == null) {
            String encoding = getCharacterEncoding();
            Charset cs = encoding == null ? Charset.defaultCharset() : Charset.forName(encoding);
            writer = new PrintWriter(new OutputStreamWriter(getOutputStream(), cs));
        }
        return writer;
    }

    public void complete() throws IOException {
        if (writer != null) {
            writer.flush();
        }
        if (stream != null) {
            stream.flush();
        }
    }

    private final class TeeServletOutputStream extends ServletOutputStream {
        private final ServletOutputStream delegate;

        private TeeServletOutputStream(ServletOutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            copy(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
            copy(b, off, len);
        }

        private void copy(byte[] b, int off, int len) {
            declaredSize += len;
            if (!buffering) {
                return;
            }
            int room = maxBytes - captured.size();
            if (room > 0) {
                captured.write(b, off, Math.min(len, room));
            }
            if (len > room) {
                truncated = true;
            }
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setWriteListener(WriteListener listener) {
            delegate.setWriteListener(listener);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
