package io.traffictape.spring.actuate;

import io.traffictape.capture.CaptureSink;
import io.traffictape.spring.TrafficTapeAutoConfiguration;
import io.traffictape.statistics.StatisticsRegistry;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Registers {@link TrafficTapeEndpoint} when Actuator is present and the endpoint is exposed.
 * Runs after core auto-config because the endpoint reads the {@link StatisticsRegistry} it creates.
 */
@AutoConfiguration(after = TrafficTapeAutoConfiguration.class)
@ConditionalOnClass({Endpoint.class, ConditionalOnAvailableEndpoint.class})
@ConditionalOnProperty(prefix = "traffictape", name = "enabled", havingValue = "true")
class TrafficTapeEndpointAutoConfiguration {

    @Bean
    @ConditionalOnBean({StatisticsRegistry.class, CaptureSink.class})
    @ConditionalOnMissingBean
    @ConditionalOnAvailableEndpoint(endpoint = TrafficTapeEndpoint.class)
    TrafficTapeEndpoint trafficTapeEndpoint(StatisticsRegistry statistics, CaptureSink sink) {
        return new TrafficTapeEndpoint(statistics, sink);
    }
}
