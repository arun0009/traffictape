package io.traffictape.sampling;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedScenarioSamplerTest {

    @Test
    void firstNPerScenario() {
        BoundedScenarioSampler sampler = new BoundedScenarioSampler(2);
        ScenarioKey key = new ScenarioKey("ep", "none", "200");
        assertThat(sampler.shouldCapture(key)).isTrue();
        assertThat(sampler.shouldCapture(key)).isTrue();
        assertThat(sampler.shouldCapture(key)).isFalse();
        assertThat(sampler.capturedCount(key)).isEqualTo(2);
    }

    @Test
    void rareStatusIsItsOwnBudget() {
        BoundedScenarioSampler sampler = new BoundedScenarioSampler(1);
        ScenarioKey ok = new ScenarioKey("ep", "none", "200");
        ScenarioKey missing = new ScenarioKey("ep", "none", "404");
        assertThat(sampler.shouldCapture(ok)).isTrue();
        assertThat(sampler.shouldCapture(ok)).isFalse();
        assertThat(sampler.shouldCapture(missing)).isTrue();
    }

    @Test
    void requestShapesAreIndependent() {
        BoundedScenarioSampler sampler = new BoundedScenarioSampler(1);
        ScenarioKey status = new ScenarioKey("ep", "{status:string}", "200");
        ScenarioKey owner = new ScenarioKey("ep", "{owner:string}", "200");
        assertThat(sampler.shouldCapture(status)).isTrue();
        assertThat(sampler.shouldCapture(owner)).isTrue();
    }

    @Test
    void concurrentReservesDoNotExceedTheBudget() throws Exception {
        BoundedScenarioSampler sampler = new BoundedScenarioSampler(5);
        ScenarioKey key = new ScenarioKey("ep", "none", "200");
        Thread[] threads = new Thread[8];
        java.util.concurrent.atomic.AtomicInteger wins = new java.util.concurrent.atomic.AtomicInteger();
        for (int t = 0; t < threads.length; t++) {
            threads[t] = new Thread(() -> {
                for (int i = 0; i < 50; i++) {
                    if (sampler.shouldCapture(key)) {
                        wins.incrementAndGet();
                    }
                }
            });
            threads[t].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        assertThat(wins.get()).isEqualTo(5);
        assertThat(sampler.capturedCount(key)).isEqualTo(5);
    }
}
