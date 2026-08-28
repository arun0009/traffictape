package io.traffictape.spring.inbound.jersey;

import io.traffictape.capture.CaptureEngine;
import io.traffictape.spring.TrafficTapeProperties;
import io.traffictape.spring.outbound.jersey.JerseyClientCaptureFilter;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jersey.ResourceConfigCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jersey / JAX-RS: inbound {@code @Path} templates on the servlet request, outbound
 * capture on a Spring {@link Client} bean. {@code ClientBuilder.newClient()} is not.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = {
        "jakarta.ws.rs.client.Client",
        "org.glassfish.jersey.server.ResourceConfig",
        "org.springframework.boot.autoconfigure.jersey.ResourceConfigCustomizer"
})
@ConditionalOnProperty(prefix = "traffictape", name = "enabled", havingValue = "true")
public class JerseyCaptureConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "trafficTapeJerseyRouteCustomizer")
    ResourceConfigCustomizer trafficTapeJerseyRouteCustomizer() {
        return (ResourceConfig config) -> config.register(JerseyRouteEnricher.class);
    }

    @Bean
    @ConditionalOnMissingBean(name = "trafficTapeJerseyClientFilter")
    JerseyClientCaptureFilter trafficTapeJerseyClientFilter(
            CaptureEngine engine,
            TrafficTapeProperties properties) {
        return new JerseyClientCaptureFilter(engine, properties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "trafficTapeJerseyClientPostProcessor")
    static BeanPostProcessor trafficTapeJerseyClientPostProcessor() {
        return new JerseyClientCapturePostProcessor();
    }
}

final class JerseyClientCapturePostProcessor implements BeanPostProcessor, BeanFactoryAware {

    private BeanFactory beanFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!(bean instanceof Client) && !(bean instanceof ClientBuilder)) {
            return bean;
        }
        JerseyClientCaptureFilter capture = filter();
        if (capture == null) {
            return bean;
        }
        try {
            if (bean instanceof ClientBuilder builder) {
                if (!hasCapture(builder.getConfiguration())) {
                    builder.register(capture);
                }
                return builder;
            }
            Client client = (Client) bean;
            if (!hasCapture(client.getConfiguration())) {
                client.register(capture);
            }
            return client;
        } catch (Throwable ignored) {
            return bean;
        }
    }

    private JerseyClientCaptureFilter filter() {
        if (beanFactory == null) {
            return null;
        }
        try {
            return beanFactory.getBean(JerseyClientCaptureFilter.class);
        } catch (NoSuchBeanDefinitionException e) {
            return null;
        }
    }

    private static boolean hasCapture(jakarta.ws.rs.core.Configuration configuration) {
        for (Object instance : configuration.getInstances()) {
            if (instance instanceof JerseyClientCaptureFilter) {
                return true;
            }
        }
        return configuration.getClasses().contains(JerseyClientCaptureFilter.class);
    }
}
