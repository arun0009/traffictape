package io.traffictape.sampling;

/**
 * Whether to keep another example for this scenario.
 * Default: {@link #shouldCapture} takes a slot atomically; {@link #recordCaptured} is for
 * custom samplers that count only after a successful enqueue.
 */
public interface Sampler {

    /** Keeps every example and tracks nothing. Used for on-demand exchanges. */
    Sampler UNBOUNDED = new Sampler() {
        @Override
        public boolean shouldCapture(ScenarioKey key) {
            return true;
        }

        @Override
        public void recordCaptured(ScenarioKey key) {
        }
    };

    boolean shouldCapture(ScenarioKey key);

    void recordCaptured(ScenarioKey key);

    /** Refund a slot reserved by {@link #shouldCapture} when the example is not stored. */
    default void release(ScenarioKey key) {
    }
}
