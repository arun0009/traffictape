/**
 * Default file backend for {@link io.traffictape.capture.CaptureSink}.
 *
 * <p>Optional sinks ({@code traffictape-sink-s3}, {@code traffictape-sink-cloudwatch})
 * replace this when configured. File output is skipped if a {@code CaptureSink} bean already exists.
 */
package io.traffictape.sink.file;
