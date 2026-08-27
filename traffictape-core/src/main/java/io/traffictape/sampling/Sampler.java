package io.traffictape.sampling;

/**
 * Whether to keep another example for this scenario.
 * Default: {@link #shouldCapture} takes a slot atomically; {@link #recordCaptured} is for
 * custom samplers that count only after a successful enqueue.
 */
public interface Sampler {

    boolean shouldCapture(ScenarioKey key);

    void recordCaptured(ScenarioKey key);
}
