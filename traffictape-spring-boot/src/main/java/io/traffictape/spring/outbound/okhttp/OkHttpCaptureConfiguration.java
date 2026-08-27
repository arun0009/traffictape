package io.traffictape.spring.outbound.okhttp;

import io.traffictape.capture.CaptureEngine;
import io.traffictape.spring.TrafficTapeProperties;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Direct {@link OkHttpClient} (and Retrofit sharing that bean). RestClient /
 * RestTemplate stay on their own interceptors.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(OkHttpClient.class)
@ConditionalOnProperty(prefix = "traffictape", name = "enabled", havingValue = "true")
public class OkHttpCaptureConfiguration {

    @Bean
    OkHttpCaptureInterceptor trafficTapeOkHttpInterceptor(
            CaptureEngine engine,
            TrafficTapeProperties properties) {
        return new OkHttpCaptureInterceptor(engine, properties);
    }

    /**
     * No constructor injection: a {@link BeanPostProcessor} that depends on other
     * beans is created too early and causes a cycle.
     */
    @Bean
    static BeanPostProcessor trafficTapeOkHttpClientPostProcessor() {
        return new OkHttpClientCapturePostProcessor();
    }
}

final class OkHttpClientCapturePostProcessor implements BeanPostProcessor, BeanFactoryAware {

    private BeanFactory beanFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!(bean instanceof OkHttpClient) && !(bean instanceof OkHttpClient.Builder)) {
            return bean;
        }
        OkHttpCaptureInterceptor capture = interceptor();
        if (capture == null) {
            return bean;
        }
        if (bean instanceof OkHttpClient.Builder builder) {
            if (!hasCapture(builder.interceptors())) {
                builder.addInterceptor(capture);
            }
            return builder;
        }
        OkHttpClient client = (OkHttpClient) bean;
        if (hasCapture(client.interceptors())) {
            return client;
        }
        return client.newBuilder().addInterceptor(capture).build();
    }

    private OkHttpCaptureInterceptor interceptor() {
        if (beanFactory == null) {
            return null;
        }
        try {
            return beanFactory.getBean(OkHttpCaptureInterceptor.class);
        } catch (NoSuchBeanDefinitionException e) {
            return null;
        }
    }

    private static boolean hasCapture(List<Interceptor> interceptors) {
        for (Interceptor candidate : interceptors) {
            if (candidate instanceof OkHttpCaptureInterceptor) {
                return true;
            }
        }
        return false;
    }
}
