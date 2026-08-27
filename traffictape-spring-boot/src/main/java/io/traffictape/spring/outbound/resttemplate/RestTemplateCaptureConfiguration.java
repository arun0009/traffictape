package io.traffictape.spring.outbound.resttemplate;

import io.traffictape.capture.CaptureEngine;
import io.traffictape.spring.TrafficTapeProperties;
import io.traffictape.spring.outbound.OutboundCaptureInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RestTemplate.class)
@ConditionalOnProperty(prefix = "traffictape", name = "enabled", havingValue = "true")
public class RestTemplateCaptureConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "trafficTapeRestTemplateCustomizer")
    RestTemplateCustomizer trafficTapeRestTemplateCustomizer(CaptureEngine engine, TrafficTapeProperties properties) {
        OutboundCaptureInterceptor interceptor = new OutboundCaptureInterceptor(engine, properties);
        return restTemplate -> restTemplate.getInterceptors().add(interceptor);
    }
}
