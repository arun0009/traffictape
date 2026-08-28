package io.traffictape.spring.outbound.jersey;

import io.traffictape.capture.CaptureEngine;
import io.traffictape.capture.CaptureQueue;
import io.traffictape.model.BodyEncoding;
import io.traffictape.model.HttpTransaction;
import io.traffictape.spring.TrafficTapeProperties;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JerseyClientCaptureFilterTest {

    @Test
    void capturesPostRequestAndResponseBodies() throws Exception {
        CaptureQueue queue = new CaptureQueue(10);
        CaptureEngine engine = CaptureEngine.createDefault(queue, 10);
        JerseyClientCaptureFilter filter = new JerseyClientCaptureFilter(engine, new TrafficTapeProperties());

        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(201)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\":\"9\"}"));
            server.start();
            try (Client client = ClientBuilder.newClient().register(filter)) {
                client.target(server.url("/ledger").uri())
                        .request()
                        .post(Entity.entity("{\"sku\":\"abc\"}", MediaType.APPLICATION_JSON_TYPE))
                        .close();
            }
        }

        assertThat(queue.size()).isEqualTo(1);
        HttpTransaction tx = queue.drain(1).get(0);
        assertThat(tx.method()).isEqualTo("POST");
        assertThat(tx.path()).isEqualTo("/ledger");
        assertThat(tx.response().status()).isEqualTo(201);
        assertThat(tx.request().body().body().toString()).contains("sku");
        assertThat(tx.response().body().body().toString()).contains("id");
        assertThat(tx.request().body().truncated()).isFalse();
    }

    @Test
    void marksRequestTruncatedWhenEntityExceedsCap() throws Exception {
        CaptureQueue queue = new CaptureQueue(10);
        CaptureEngine engine = CaptureEngine.createDefault(queue, 10);
        TrafficTapeProperties properties = new TrafficTapeProperties();
        properties.setMaxRequestBytes(8);
        JerseyClientCaptureFilter filter = new JerseyClientCaptureFilter(engine, properties);

        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
            server.start();
            try (Client client = ClientBuilder.newClient().register(filter)) {
                client.target(server.url("/ledger").uri())
                        .request()
                        .post(Entity.entity("{\"sku\":\"abcdefghij\"}", MediaType.APPLICATION_JSON_TYPE))
                        .close();
            }
        }

        HttpTransaction tx = queue.drain(1).get(0);
        assertThat(tx.request().body().truncated()).isTrue();
        assertThat(tx.request().body().encoding()).isEqualTo(BodyEncoding.OMITTED);
        assertThat(tx.request().body().sizeBytes()).isGreaterThan(8);
    }

    @Test
    void capturesJsonPojoEntityNotOnlyString() throws Exception {
        CaptureQueue queue = new CaptureQueue(10);
        CaptureEngine engine = CaptureEngine.createDefault(queue, 10);
        JerseyClientCaptureFilter filter = new JerseyClientCaptureFilter(engine, new TrafficTapeProperties());

        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(201)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\":\"9\"}"));
            server.start();
            try (Client client = ClientBuilder.newClient()
                    .register(filter)
                    .register(org.glassfish.jersey.jackson.JacksonFeature.class)) {
                client.target(server.url("/ledger").uri())
                        .request()
                        .post(Entity.entity(java.util.Map.of("sku", "abc"), MediaType.APPLICATION_JSON_TYPE))
                        .close();
            }
            assertThat(server.takeRequest().getBody().readUtf8()).contains("sku");
        }

        HttpTransaction tx = queue.drain(1).get(0);
        assertThat(tx.request().body().body().toString()).contains("sku");
        assertThat(tx.response().body().body().toString()).contains("id");
    }
}
