package io.traffictape.spring.outbound.webclient;

import io.traffictape.capture.CaptureEngine;
import io.traffictape.capture.CaptureQueue;
import io.traffictape.model.BodyEncoding;
import io.traffictape.model.HttpTransaction;
import io.traffictape.spring.TrafficTapeProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WebClientCaptureFilterTest {

    @Test
    void capturesJsonRequestBodyFromBodyValue() throws Exception {
        CaptureQueue queue = new CaptureQueue(10);
        CaptureEngine engine = CaptureEngine.createDefault(queue, 10);
        WebClientCaptureFilter filter = new WebClientCaptureFilter(engine, new TrafficTapeProperties());

        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(201)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\":\"9\"}"));
            server.start();
            WebClient client = WebClient.builder().filter(filter).build();
            String body = client.post()
                    .uri(server.url("/ledger").uri())
                    .bodyValue(Map.of("sku", "abc"))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            assertThat(body).contains("id");
            assertThat(server.takeRequest().getBody().readUtf8()).contains("sku");
        }

        HttpTransaction tx = queue.poll(java.time.Duration.ofSeconds(2));
        assertThat(tx).isNotNull();
        assertThat(tx.method()).isEqualTo("POST");
        assertThat(tx.path()).isEqualTo("/ledger");
        assertThat(tx.response().status()).isEqualTo(201);
        assertThat(tx.request().body().encoding()).isEqualTo(BodyEncoding.JSON);
        assertThat(tx.request().body().body().toString()).contains("sku");
        assertThat(tx.response().body().body().toString()).contains("id");
    }
}
