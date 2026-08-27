package io.traffictape.sampling;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded first-N sampler keyed by {@link ScenarioKey}.
 * {@link #shouldCapture} reserves a slot atomically so concurrent requests cannot
 * exceed the budget. A full queue still consumes a reserved slot; that is cheaper
 * than writing more bodies than configured.
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
        AtomicInteger count = captured.computeIfAbsent(key, k -> new AtomicInteger());
        while (true) {
            int n = count.get();
            if (n >= maxExamplesPerScenario) {
                return false;
            }
            if (count.compareAndSet(n, n + 1)) {
                return true;
            }
        }
    }

    /**
     * Slot reservation happens in {@link #shouldCapture}. Kept so a custom {@link Sampler}
     * can count only after a successful enqueue.
     */
    @Override
    public void recordCaptured(ScenarioKey key) {
    }

    public int capturedCount(ScenarioKey key) {
        AtomicInteger count = captured.get(key);
        return count == null ? 0 : count.get();
    }

    public int maxExamplesPerScenario() {
        return maxExamplesPerScenario;
    }
}
