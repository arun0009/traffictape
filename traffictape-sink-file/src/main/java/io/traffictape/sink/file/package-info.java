/**
 * Default file backend for {@link io.traffictape.capture.CaptureSink}.
 *
 * <p>Optional sinks ({@code traffictape-sink-s3}, {@code traffictape-sink-cloudwatch})
 * replace this when configured. File output backs off via
 * {@code @ConditionalOnMissingBean}.
 */
package io.traffictape.sink.file;
