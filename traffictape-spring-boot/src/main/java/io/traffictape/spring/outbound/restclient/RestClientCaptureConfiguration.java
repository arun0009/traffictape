package io.traffictape.spring.outbound.restclient;

import io.traffictape.capture.CaptureEngine;
import io.traffictape.spring.TrafficTapeProperties;
import io.traffictape.spring.outbound.OutboundCaptureInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RestClient.class)
@ConditionalOnProperty(prefix = "traffictape", name = "enabled", havingValue = "true")
public class RestClientCaptureConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "trafficTapeRestClientCustomizer")
    RestClientCustomizer trafficTapeRestClientCustomizer(CaptureEngine engine, TrafficTapeProperties properties) {
        OutboundCaptureInterceptor interceptor = new OutboundCaptureInterceptor(engine, properties);
        return builder -> builder.requestInterceptor(interceptor);
    }
}
