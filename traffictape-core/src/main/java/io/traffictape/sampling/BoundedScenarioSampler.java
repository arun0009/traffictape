package io.traffictape.sampling;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * First-N sampler keyed by {@link ScenarioKey}. Unique keys are capped so a long
 * run cannot grow the map without bound. A full queue should {@link #release} the slot.
 */
public final class BoundedScenarioSampler implements Sampler {

    private final int maxExamplesPerScenario;
    private final int maxUniqueKeys;
    private final ConcurrentHashMap<ScenarioKey, AtomicInteger> captured = new ConcurrentHashMap<>();

    public BoundedScenarioSampler(int maxExamplesPerScenario) {
        this(maxExamplesPerScenario, 10_000);
    }

    public BoundedScenarioSampler(int maxExamplesPerScenario, int maxUniqueKeys) {
        this.maxExamplesPerScenario = Math.max(0, maxExamplesPerScenario);
        this.maxUniqueKeys = Math.max(16, maxUniqueKeys);
    }

    @Override
    public boolean shouldCapture(ScenarioKey key) {
        if (maxExamplesPerScenario == 0 || key == null) {
            return false;
        }
        if (captured.size() >= maxUniqueKeys && !captured.containsKey(key)) {
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

    @Override
    public void recordCaptured(ScenarioKey key) {
    }

    @Override
    public void release(ScenarioKey key) {
        if (key == null) {
            return;
        }
        AtomicInteger count = captured.get(key);
        if (count == null) {
            return;
        }
        while (true) {
            int n = count.get();
            if (n <= 0) {
                return;
            }
            if (count.compareAndSet(n, n - 1)) {
                return;
            }
        }
    }

    public int capturedCount(ScenarioKey key) {
        AtomicInteger count = captured.get(key);
        return count == null ? 0 : count.get();
    }

    public int maxExamplesPerScenario() {
        return maxExamplesPerScenario;
    }

    public int uniqueKeys() {
        return captured.size();
    }
}
