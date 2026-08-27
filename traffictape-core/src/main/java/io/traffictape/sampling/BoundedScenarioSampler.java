package io.traffictape.sampling;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded first-N sampler keyed by {@link ScenarioKey}.
 * Each distinct endpoint + request shape + response characteristic gets its own budget.
 */
public final class BoundedScenarioSampler implements Sampler {

    private final int maxExamplesPerScenario;
    private final ConcurrentHashMap<ScenarioKey, AtomicInteger> captured = new ConcurrentHashMap<>();

    public BoundedScenarioSampler(int maxExamplesPerScenario) {
        this.maxExamplesPerScenario = Math.max(0, maxExamplesPerScenario);
    }

    @Override
    public boolean shouldCapture(ScenarioKey key) {
        if (maxExamplesPerScenario == 0 || key == null) {
            return false;
        }
        AtomicInteger count = captured.get(key);
        return count == null || count.get() < maxExamplesPerScenario;
    }

    @Override
    public void recordCaptured(ScenarioKey key) {
        if (key == null) {
            return;
        }
        captured.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
    }

    public int capturedCount(ScenarioKey key) {
        AtomicInteger count = captured.get(key);
        return count == null ? 0 : count.get();
    }

    public int maxExamplesPerScenario() {
        return maxExamplesPerScenario;
    }
}
