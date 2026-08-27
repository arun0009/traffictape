package io.traffictape.sink.cloudwatch;

import io.traffictape.capture.CaptureSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;

/**
 * Replaces the file sink when {@code traffictape.output.cloudwatch.log-group} is set.
 * Runs after S3 (if both are on the classpath and configured, S3 wins) and
 * before the Spring file auto-config.
 */
@AutoConfiguration(
        afterName = "io.traffictape.sink.s3.TrafficTapeS3AutoConfiguration",
        beforeName = "io.traffictape.spring.TrafficTapeAutoConfiguration")
@ConditionalOnClass(CloudWatchLogsClient.class)
@ConditionalOnProperty(prefix = "traffictape", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TrafficTapeCloudWatchProperties.class)
class TrafficTapeCloudWatchAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TrafficTapeCloudWatchAutoConfiguration.class);

    @Bean
    @ConditionalOnProperty(prefix = "traffictape.output.cloudwatch", name = "log-group")
    @ConditionalOnMissingBean(CloudWatchLogsClient.class)
    CloudWatchLogsClient trafficTapeCloudWatchLogsClient(TrafficTapeCloudWatchProperties properties) {
        var builder = CloudWatchLogsClient.builder();
        if (properties.getRegion() != null && !properties.getRegion().isBlank()) {
            builder.region(Region.of(properties.getRegion().trim()));
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "traffictape.output.cloudwatch", name = "log-group")
    @ConditionalOnMissingBean(CaptureSink.class)
    CaptureSink trafficTapeCloudWatchSink(
            CloudWatchLogsClient client,
            TrafficTapeCloudWatchProperties properties) {
        String stream = StreamName.resolve(properties);
        log.info("TrafficTape corpus → cloudwatch://{}/{}", properties.getLogGroup(), stream);
        return new CloudWatchCaptureSink(client, properties.getLogGroup().trim(), stream);
    }
}
