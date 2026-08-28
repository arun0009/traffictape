package io.traffictape.spring;

import io.traffictape.capture.InMemoryCaptureSink;
import io.traffictape.model.Direction;
import io.traffictape.model.HttpTransaction;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = TrafficTapeJerseyTest.App.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "traffictape.enabled=true",
                "traffictape.output.directory=${java.io.tmpdir}/traffictape-jersey",
                "traffictape.flush.interval=20ms",
                "traffictape.flush.max-events=1",
                "spring.jersey.application-path=/jersey"
        })
@Import(TrafficTapeJerseyTest.MemSinkConfig.class)
class TrafficTapeJerseyTest {

    @LocalServerPort
    int port;

    @Autowired
    InMemoryCaptureSink sink;

    @Test
    void inboundJerseyTemplateAndClientBeanOutbound() throws Exception {
        sink.clear();
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/jersey/catalog/widgets")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        HttpTransaction inbound = null;
        HttpTransaction outbound = null;
        while (System.nanoTime() < deadline) {
            inbound = sink.written().stream()
                    .filter(tx -> tx.direction() == Direction.INBOUND && "/catalog/{kind}".equals(tx.route()))
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
        assertThat(inbound.path()).isEqualTo("/jersey/catalog/widgets");
        assertThat(inbound.route()).isEqualTo("/catalog/{kind}");
        assertThat(outbound).isNotNull();
        assertThat(outbound.path()).contains("/warehouse/widgets");
        assertThat(outbound.method()).isEqualTo("GET");
    }

    @SpringBootApplication
    static class App {
        @Bean
        ResourceConfig jerseyConfig(CatalogResource catalog, WarehouseResource warehouse) {
            return new ResourceConfig()
                    .register(catalog)
                    .register(warehouse);
        }

        @Bean
        Client jaxRsClient() {
            return ClientBuilder.newClient();
        }

        @Bean
        CatalogResource catalogResource(Client client, Environment env) {
            return new CatalogResource(client, env);
        }

        @Bean
        WarehouseResource warehouseResource() {
            return new WarehouseResource();
        }
    }

    @Path("/catalog")
    public static class CatalogResource {
        private final Client client;
        private final Environment env;

        CatalogResource(Client client, Environment env) {
            this.client = client;
            this.env = env;
        }

        @GET
        @Path("{kind}")
        @Produces(MediaType.APPLICATION_JSON)
        public String get(@PathParam("kind") String kind) {
            String localPort = env.getProperty("local.server.port");
            try (Response ignored = client.target("http://127.0.0.1:" + localPort + "/jersey/warehouse/" + kind)
                    .request()
                    .get()) {
                return "{\"kind\":\"" + kind + "\"}";
            }
        }
    }

    @Path("/warehouse")
    public static class WarehouseResource {
        @GET
        @Path("{kind}")
        @Produces(MediaType.APPLICATION_JSON)
        public String get(@PathParam("kind") String kind) {
            return "{\"kind\":\"" + kind + "\",\"qty\":3}";
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
