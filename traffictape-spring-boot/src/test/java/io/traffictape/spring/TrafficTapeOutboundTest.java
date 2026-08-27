package io.traffictape.spring;

import io.traffictape.capture.InMemoryCaptureSink;
import io.traffictape.model.Direction;
import io.traffictape.model.HttpTransaction;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = TrafficTapeOutboundTest.App.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "traffictape.enabled=true",
                "traffictape.output.directory=${java.io.tmpdir}/traffictape-outbound",
                "traffictape.flush.interval=20ms",
                "traffictape.flush.max-events=1"
        })
@Import(TrafficTapeOutboundTest.MemSinkConfig.class)
class TrafficTapeOutboundTest {

    @LocalServerPort
    int port;

    @Autowired
    RestClient.Builder restClientBuilder;

    @Autowired
    InMemoryCaptureSink sink;

    @Test
    void inboundCallCapturesCorrelatedOutbound() throws Exception {
        sink.clear();
        RestClient client = restClientBuilder.baseUrl("http://127.0.0.1:" + port).build();
        Map<?, ?> body = client.get().uri("/orders/42").retrieve().body(Map.class);
        assertThat(body).isNotNull();

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
        assertThat(inbound.correlation().exchangeId()).isEqualTo(outbound.correlation().parentExchangeId());
        assertThat(outbound.correlation().sequence()).isEqualTo(1);
        assertThat(inbound.correlation().outboundCount()).isEqualTo(1);
        assertThat(outbound.method()).isEqualTo("GET");
        assertThat(outbound.route()).contains("/inventory/");
    }

    @Test
    void outboundPostIsCaptured() throws Exception {
        sink.clear();
        RestClient client = restClientBuilder.baseUrl("http://127.0.0.1:" + port).build();
        client.post().uri("/orders").body(Map.of("sku", "abc")).retrieve().body(Map.class);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (sink.written().stream().noneMatch(tx -> tx.direction() == Direction.OUTBOUND && "POST".equals(tx.method()))
                && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        HttpTransaction outbound = sink.written().stream()
                .filter(tx -> tx.direction() == Direction.OUTBOUND && "POST".equals(tx.method()))
                .findFirst()
                .orElseThrow();
        assertThat(outbound.request().body()).isNotNull();
    }

    @SpringBootApplication
    static class App {
        @Bean
        RestClient inventoryClient(RestClient.Builder builder, @org.springframework.beans.factory.annotation.Value("${local.server.port:0}") int ignored) {
            return builder.build();
        }

        @RestController
        static class Api {
            private final RestClient.Builder builder;
            private final org.springframework.core.env.Environment env;

            Api(RestClient.Builder builder, org.springframework.core.env.Environment env) {
                this.builder = builder;
                this.env = env;
            }

            @GetMapping("/orders/{id}")
            Map<String, Object> get(@PathVariable String id) {
                String port = env.getProperty("local.server.port");
                RestClient client = builder.baseUrl("http://127.0.0.1:" + port).build();
                Map<?, ?> inv = client.get().uri("/inventory/{sku}", id).retrieve().body(Map.class);
                return Map.of("id", id, "inventory", inv);
            }

            @PostMapping("/orders")
            Map<String, Object> create(@RequestBody Map<String, String> body) {
                String port = env.getProperty("local.server.port");
                RestClient client = builder.baseUrl("http://127.0.0.1:" + port).build();
                client.post().uri("/ledger").body(Map.of("sku", body.get("sku"))).retrieve().toBodilessEntity();
                return Map.of("id", "9");
            }

            @GetMapping("/inventory/{sku}")
            Map<String, Object> inventory(@PathVariable String sku) {
                return Map.of("sku", sku, "qty", 3);
            }

            @PostMapping("/ledger")
            Map<String, String> ledger(@RequestBody Map<String, String> body) {
                return Map.of("ok", "true");
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
