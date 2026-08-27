/**
 * Capture engine and extension SPIs.
 *
 * <p><b>Entry point:</b> {@link io.traffictape.capture.CaptureEngine#record}.
 * Adapters build an {@link io.traffictape.capture.ObservedExchange} and call {@code record}.
 * That is the whole adapter contract.
 *
 * <p><b>SPIs</b> (replace with a {@code @Bean}; file/Spring defaults back off):
 * <ul>
 *   <li>{@link io.traffictape.capture.CaptureSink} — write the corpus (file today; S3 later)</li>
 *   <li>{@link io.traffictape.fingerprint.Fingerprinter} — endpoint + scenario identity</li>
 *   <li>{@link io.traffictape.sampling.Sampler} — which scenarios keep example bodies</li>
 *   <li>{@link io.traffictape.capture.CaptureMetrics} — NOOP in core; Micrometer when a registry exists</li>
 * </ul>
 */
package io.traffictape.capture;
