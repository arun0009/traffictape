package io.traffictape.spring;

import io.traffictape.capture.InMemoryCaptureSink;
import io.traffictape.model.Direction;
import io.traffictape.model.HttpTransaction;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = TrafficTapeOkHttpTest.App.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "traffictape.enabled=true",
                "traffictape.output.directory=${java.io.tmpdir}/traffictape-okhttp",
                "traffictape.flush.interval=20ms",
                "traffictape.flush.max-events=1"
        })
@Import(TrafficTapeOkHttpTest.MemSinkConfig.class)
class TrafficTapeOkHttpTest {

    @LocalServerPort
    int port;

    @Autowired
    OkHttpClient okHttpClient;

    @Autowired
    InMemoryCaptureSink sink;

    @Test
    void inboundCallCapturesOkHttpOutbound() throws Exception {
        sink.clear();
        okHttpClient.newCall(new Request.Builder()
                        .url("http://127.0.0.1:" + port + "/orders/42")
                        .build())
                .execute()
                .close();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        HttpTransaction inbound = null;
        HttpTransaction outbound = null;
        while (System.nanoTime() < deadline) {
            inbound = sink.written().stream()
                    .filter(tx -> tx.direction() == Direction.INBOUND && "/orders/{id}".equals(tx.route()))
                    .findFirst()
                    .orElse(null);
            if (inbound != null) {
                String id = inbound.correlation().exchangeId();
                outbound = sink.written().stream()
                        .filter(tx -> tx.direction() == Direction.OUTBOUND
                                && tx.correlation() != null
                                && id.equals(tx.correlation().parentExchangeId()))
                        .findFirst()
                        .orElse(null);
            }
            if (inbound != null && outbound != null) {
                break;
            }
            Thread.sleep(20);
        }
        assertThat(inbound).isNotNull();
        assertThat(outbound).isNotNull();
        assertThat(outbound.path()).contains("/inventory/");
        assertThat(outbound.method()).isEqualTo("GET");
    }

    @SpringBootApplication
    static class App {
        @Bean
        OkHttpClient okHttpClient() {
            return new OkHttpClient();
        }

        @RestController
        static class Api {
            private final OkHttpClient client;
            private final org.springframework.core.env.Environment env;

            Api(OkHttpClient client, org.springframework.core.env.Environment env) {
                this.client = client;
                this.env = env;
            }

            @GetMapping("/orders/{id}")
            Map<String, Object> get(@PathVariable String id) throws Exception {
                String port = env.getProperty("local.server.port");
                client.newCall(new Request.Builder()
                                .url("http://127.0.0.1:" + port + "/inventory/" + id)
                                .build())
                        .execute()
                        .close();
                return Map.of("id", id);
            }

            @GetMapping("/inventory/{sku}")
            Map<String, Object> inventory(@PathVariable String sku) {
                return Map.of("sku", sku, "qty", 3);
            }
        }
    }

    @TestConfiguration
    static class MemSinkConfig {
        @Bean
        @Primary
        InMemoryCaptureSink inMemoryCaptureSink() {
            return new InMemoryCaptureSink();
        }
    }
}
