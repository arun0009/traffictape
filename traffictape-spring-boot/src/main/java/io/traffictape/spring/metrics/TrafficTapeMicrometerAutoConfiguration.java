package io.traffictape.spring.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.traffictape.capture.CaptureMetrics;
import io.traffictape.spring.TrafficTapeAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Micrometer adapter. Runs before core auto-config so {@link CaptureMetrics#NOOP} is only used
 * when no {@link MeterRegistry} (and no user {@code @Bean CaptureMetrics}) exists.
 */
@AutoConfiguration(before = TrafficTapeAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(prefix = "traffictape", name = "enabled", havingValue = "true")
class TrafficTapeMicrometerAutoConfiguration {

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(CaptureMetrics.class)
    CaptureMetrics trafficTapeMicrometerMetrics(MeterRegistry registry) {
        CaptureMetrics metrics = new MicrometerCaptureMetrics(registry);
        metrics.setEnabled(true);
        return metrics;
    }
}
