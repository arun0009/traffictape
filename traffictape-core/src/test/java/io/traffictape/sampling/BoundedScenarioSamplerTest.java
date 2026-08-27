package io.traffictape.sampling;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedScenarioSamplerTest {

    @Test
    void firstNPerScenario() {
        BoundedScenarioSampler sampler = new BoundedScenarioSampler(2);
        ScenarioKey key = new ScenarioKey("ep", "none", "200");
        assertThat(sampler.shouldCapture(key)).isTrue();
        sampler.recordCaptured(key);
        sampler.recordCaptured(key);
        assertThat(sampler.shouldCapture(key)).isFalse();
    }

    @Test
    void rareStatusIsItsOwnBudget() {
        BoundedScenarioSampler sampler = new BoundedScenarioSampler(1);
        ScenarioKey ok = new ScenarioKey("ep", "none", "200");
        ScenarioKey missing = new ScenarioKey("ep", "none", "404");
        sampler.recordCaptured(ok);
        assertThat(sampler.shouldCapture(ok)).isFalse();
        assertThat(sampler.shouldCapture(missing)).isTrue();
    }

    @Test
    void requestShapesAreIndependent() {
        BoundedScenarioSampler sampler = new BoundedScenarioSampler(1);
        ScenarioKey status = new ScenarioKey("ep", "{status:string}", "200");
        ScenarioKey owner = new ScenarioKey("ep", "{owner:string}", "200");
        sampler.recordCaptured(status);
        assertThat(sampler.shouldCapture(owner)).isTrue();
    }
}
