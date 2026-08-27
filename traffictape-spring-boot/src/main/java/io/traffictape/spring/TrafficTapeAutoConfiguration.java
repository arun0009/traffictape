package io.traffictape.spring;

import io.traffictape.body.BodyCodec;
import io.traffictape.capture.AsyncCaptureWorker;
import io.traffictape.capture.CaptureEngine;
import io.traffictape.capture.CaptureMetrics;
import io.traffictape.capture.CaptureQueue;
import io.traffictape.capture.CaptureSink;
import io.traffictape.capture.JsonSupport;
import io.traffictape.fingerprint.DefaultFingerprinter;
import io.traffictape.fingerprint.Fingerprinter;
import io.traffictape.fingerprint.JsonShapeExtractor;
import io.traffictape.fingerprint.PathNormalizer;
import io.traffictape.policy.CapturePolicy;
import io.traffictape.redaction.Redactor;
import io.traffictape.sampling.BoundedScenarioSampler;
import io.traffictape.sampling.Sampler;
import io.traffictape.sink.file.FileCaptureSink;
import io.traffictape.spring.inbound.InboundTrafficTapeFilter;
import io.traffictape.spring.outbound.okhttp.OkHttpCaptureConfiguration;
import io.traffictape.spring.outbound.restclient.RestClientCaptureConfiguration;
import io.traffictape.spring.outbound.resttemplate.RestTemplateCaptureConfiguration;
import io.traffictape.spring.outbound.webclient.WebClientCaptureConfiguration;
import io.traffictape.statistics.StatisticsRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wires core SPIs. To extend: expose a {@code @Bean} of {@link CaptureSink},
 * {@link Fingerprinter}, {@link Sampler}, or {@link CaptureMetrics}.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "traffictape", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TrafficTapeProperties.class)
@Import({
        RestClientCaptureConfiguration.class,
        RestTemplateCaptureConfiguration.class,
        WebClientCaptureConfiguration.class,
        OkHttpCaptureConfiguration.class
})
public class TrafficTapeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    CapturePolicy trafficTapeCapturePolicy(TrafficTapeProperties properties) {
        TrafficTapeProperties.Capture capture = properties.getCapture();
        return CapturePolicy.builder()
                .includeMethods(capture.getInclude().getMethods())
                .excludeRoutes(capture.getExclude().getRoutes())
                .excludeContentTypes(capture.getExclude().getContentTypes())
                .excludeDestinations(capture.getExclude().getDestinations())
                .excludeHeaders(properties.getRedaction().getHeaders())
                .includeHeaders(capture.getInclude().getHeaders())
                .excludeJsonFields(properties.getRedaction().getJsonFields())
                .includeJsonFields(capture.getInclude().getJsonFields())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    PathNormalizer trafficTapePathNormalizer() {
        return new PathNormalizer();
    }

    @Bean
    @ConditionalOnMissingBean
    Fingerprinter trafficTapeFingerprinter() {
        return new DefaultFingerprinter();
    }

    @Bean
    @ConditionalOnMissingBean
    JsonShapeExtractor trafficTapeJsonShapeExtractor() {
        return new JsonShapeExtractor(JsonSupport.mapper());
    }

    @Bean
    @ConditionalOnMissingBean
    Redactor trafficTapeRedactor(CapturePolicy policy) {
        return new Redactor(policy);
    }

    @Bean
    @ConditionalOnMissingBean
    BodyCodec trafficTapeBodyCodec(Redactor redactor, TrafficTapeProperties properties) {
        return new BodyCodec(JsonSupport.mapper(), redactor, properties.getMaxRequestBytes());
    }

    @Bean
    @ConditionalOnMissingBean
    Sampler trafficTapeSampler(TrafficTapeProperties properties) {
        return new BoundedScenarioSampler(properties.getMaxExamplesPerScenario());
    }

    @Bean
    @ConditionalOnMissingBean
    CaptureQueue trafficTapeCaptureQueue(TrafficTapeProperties properties) {
        return new CaptureQueue(properties.getQueueSize());
    }

    @Bean
    @ConditionalOnMissingBean
    StatisticsRegistry trafficTapeStatistics(TrafficTapeProperties properties) {
        return new StatisticsRegistry(
                properties.getMaxUniqueFingerprints(),
                properties.getMaxExamplesPerScenario(),
                properties.getPlateauAfter());
    }

    @Bean
    @ConditionalOnMissingBean
    CaptureEngine trafficTapeCaptureEngine(
            TrafficTapeProperties properties,
            CapturePolicy policy,
            PathNormalizer pathNormalizer,
            Fingerprinter fingerprinter,
            JsonShapeExtractor shapeExtractor,
            Sampler sampler,
            CaptureQueue queue,
            StatisticsRegistry statistics,
            BodyCodec bodyCodec,
            Redactor redactor,
            CaptureMetrics metrics) {
        return CaptureEngine.builder()
                .policy(policy)
                .pathNormalizer(pathNormalizer)
                .fingerprinter(fingerprinter)
                .shapeExtractor(shapeExtractor)
                .sampler(sampler)
                .queue(queue)
                .statistics(statistics)
                .bodyCodec(bodyCodec)
                .redactor(redactor)
                .metrics(metrics)
                .maxExamplesPerScenario(properties.getMaxExamplesPerScenario())
                .plateauAfter(properties.getPlateauAfter())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(CaptureSink.class)
    CaptureSink trafficTapeCaptureSink(TrafficTapeProperties properties, Environment environment) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("recorderVersion", "0.1.0-SNAPSHOT");
        meta.put("serviceName", environment.getProperty("spring.application.name", "application"));
        meta.put("environment", environment.getProperty("spring.profiles.active",
                environment.getProperty("ENVIRONMENT", "")));
        meta.put("outputDirectory", properties.getOutput().getDirectory());
        return new FileCaptureSink(
                Path.of(properties.getOutput().getDirectory()),
                meta,
                properties.getFlush().getMaxEvents(),
                properties.getFlush().getMaxBytes()
        );
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    @ConditionalOnMissingBean
    AsyncCaptureWorker trafficTapeWorker(
            CaptureQueue queue,
            CaptureSink sink,
            StatisticsRegistry statistics,
            CaptureMetrics metrics,
            TrafficTapeProperties properties) {
        return new AsyncCaptureWorker(
                queue,
                sink,
                statistics,
                metrics,
                properties.getFlush().getMaxEvents(),
                properties.getFlush().getMaxBytes(),
                properties.getFlush().getInterval(),
                properties.getShutdownDrain()
        );
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    FilterRegistrationBean<InboundTrafficTapeFilter> trafficTapeInboundFilter(
            CaptureEngine engine,
            TrafficTapeProperties properties) {
        FilterRegistrationBean<InboundTrafficTapeFilter> reg =
                new FilterRegistrationBean<>(new InboundTrafficTapeFilter(engine, properties));
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        reg.setName("trafficTapeInboundFilter");
        return reg;
    }

    @Bean
    @ConditionalOnMissingBean(CaptureMetrics.class)
    CaptureMetrics trafficTapeNoopMetrics() {
        return CaptureMetrics.NOOP;
    }
}
