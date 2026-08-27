package io.traffictape.statistics;

import io.traffictape.model.Direction;
import io.traffictape.model.Fingerprint;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class StatisticsRegistryTest {

    @Test
    void lastNewScenarioAtMovesOnlyWhenANewScenarioAppears() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-27T00:00:00Z"));
        StatisticsRegistry registry = new StatisticsRegistry(32, 50, Duration.ofHours(6), clock);

        observe(registry, "s1", "/a", clock.instant());
        Instant first = registry.snapshot().lastNewScenarioAt();
        assertThat(first).isEqualTo(clock.instant());

        clock.plus(Duration.ofMinutes(10));
        observe(registry, "s1", "/a", clock.instant());
        assertThat(registry.snapshot().lastNewScenarioAt()).isEqualTo(first);

        clock.plus(Duration.ofMinutes(10));
        observe(registry, "s2", "/b", clock.instant());
        assertThat(registry.snapshot().lastNewScenarioAt()).isEqualTo(clock.instant());
    }

    @Test
    void captureReadyAfterPlateauWithNoNewScenarios() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-27T00:00:00Z"));
        StatisticsRegistry registry = new StatisticsRegistry(32, 50, Duration.ofHours(6), clock);
        observe(registry, "s1", "/a", clock.instant());

        clock.plus(Duration.ofHours(5));
        observe(registry, "s1", "/a", clock.instant());
        assertThat(registry.snapshot().captureReady()).isFalse();

        clock.plus(Duration.ofHours(2));
        assertThat(registry.snapshot().captureReady()).isTrue();
        assertThat(registry.snapshot().plateauAfterSeconds()).isEqualTo(Duration.ofHours(6).toSeconds());
    }

    @Test
    void bodiesCompleteIsMinOfCountAndN() {
        StatisticsRegistry few = new StatisticsRegistry(32, 50, Duration.ZERO);
        Fingerprint nightly = new Fingerprint("nightly", "INBOUND GET /jobs/nightly");
        for (int i = 0; i < 3; i++) {
            observe(few, nightly, "/jobs/nightly", Instant.now());
            few.recordCaptured(nightly, 12);
        }
        assertThat(few.snapshot().gaps().getFirst().bodiesComplete()).isTrue();

        StatisticsRegistry hot = new StatisticsRegistry(32, 2, Duration.ZERO);
        Fingerprint busy = new Fingerprint("hot", "INBOUND GET /hot");
        for (int i = 0; i < 10; i++) {
            observe(hot, busy, "/hot", Instant.now());
            if (i < 2) {
                hot.recordCaptured(busy, 8);
            }
        }
        StatisticsRegistry.Gap gap = hot.snapshot().gaps().getFirst();
        assertThat(gap.count()).isEqualTo(10);
        assertThat(gap.capturedExamples()).isEqualTo(2);
        assertThat(gap.bodiesComplete()).isTrue();
    }

    private static void observe(StatisticsRegistry registry, String id, String route, Instant now) {
        observe(registry, new Fingerprint(id, "INBOUND GET " + route), route, now);
    }

    private static void observe(StatisticsRegistry registry, Fingerprint scenario, String route, Instant now) {
        Fingerprint endpoint = new Fingerprint("e-" + scenario.id(), "INBOUND GET " + route);
        registry.recordObservation(Direction.INBOUND, "GET", route, endpoint, scenario, 200, 1, 10, 10, now);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void plus(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
