package io.traffictape.sampling;

/**
 * Decides whether a representative example should be retained for a scenario.
 * {@link #shouldCapture} may reserve a slot; {@link #recordCaptured} confirms
 * it after a successful enqueue. The default sampler reserves atomically in
 * {@code shouldCapture} so concurrent requests cannot exceed the budget.
 *
 * <p>To replace: implement this and expose a {@code @Bean Sampler}. The default
 * backs off via {@code @ConditionalOnMissingBean}.
 */
public interface Sampler {

    boolean shouldCapture(ScenarioKey key);

    void recordCaptured(ScenarioKey key);
}
