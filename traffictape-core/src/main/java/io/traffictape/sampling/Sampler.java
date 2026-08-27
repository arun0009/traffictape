package io.traffictape.sampling;

/**
 * Decides whether a representative example should be retained for a scenario.
 * Implementations must be bounded. First-N per scenario is the v0.1 strategy.
 *
 * <p>To replace: implement this and expose a {@code @Bean Sampler}. The default
 * backs off via {@code @ConditionalOnMissingBean}.
 */
public interface Sampler {

    boolean shouldCapture(ScenarioKey key);

    void recordCaptured(ScenarioKey key);
}
