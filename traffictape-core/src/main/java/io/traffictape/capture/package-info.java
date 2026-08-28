/**
 * Capture engine. Adapters build {@link io.traffictape.capture.ObservedExchange}
 * and call {@link io.traffictape.capture.CaptureEngine#record}. Backends implement
 * {@link io.traffictape.capture.CaptureSink}.
 */
package io.traffictape.capture;
