package io.traffictape.sink.s3;

import io.traffictape.capture.CaptureSink;
import io.traffictape.capture.ObjectStoreCaptureSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Replaces the file sink when {@code traffictape.output.s3.bucket} is set.
 * Runs before Spring file auto-config so {@code @ConditionalOnMissingBean(CaptureSink)}
 * on the file writer backs off.
 */
@AutoConfiguration(beforeName = "io.traffictape.spring.TrafficTapeAutoConfiguration")
@ConditionalOnClass(S3Client.class)
@ConditionalOnProperty(prefix = "traffictape", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TrafficTapeS3Properties.class)
class TrafficTapeS3AutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TrafficTapeS3AutoConfiguration.class);

    @Bean
    @ConditionalOnProperty(prefix = "traffictape.output.s3", name = "bucket")
    @ConditionalOnMissingBean(S3Client.class)
    S3Client trafficTapeS3Client(TrafficTapeS3Properties properties) {
        var builder = S3Client.builder();
        if (properties.getRegion() != null && !properties.getRegion().isBlank()) {
            builder.region(Region.of(properties.getRegion().trim()));
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "traffictape.output.s3", name = "bucket")
    @ConditionalOnMissingBean(CaptureSink.class)
    CaptureSink trafficTapeS3Sink(
            S3Client client,
            TrafficTapeS3Properties properties,
            Environment environment) {
        String service = environment.getProperty("spring.application.name", "application");
        String prefix = InstancePrefix.resolve(properties, service);
        String location = "s3://" + properties.getBucket() + "/" + prefix;
        log.info("TrafficTape corpus → {}", location);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("recorderVersion", "0.1.0-SNAPSHOT");
        meta.put("serviceName", service);
        meta.put("environment", environment.getProperty("spring.profiles.active",
                environment.getProperty("ENVIRONMENT", "")));
        meta.put("output", location);
        return new ObjectStoreCaptureSink(new S3ObjectPutter(client, properties.getBucket(), prefix), meta);
    }
}
