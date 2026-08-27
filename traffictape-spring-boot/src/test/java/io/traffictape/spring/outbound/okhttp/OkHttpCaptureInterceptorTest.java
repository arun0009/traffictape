package io.traffictape.spring.outbound.okhttp;

import io.traffictape.capture.CaptureEngine;
import io.traffictape.capture.CaptureQueue;
import io.traffictape.model.HttpTransaction;
import io.traffictape.spring.TrafficTapeProperties;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OkHttpCaptureInterceptorTest {

    @Test
    void capturesPostRequestAndResponseBodies() throws Exception {
        CaptureQueue queue = new CaptureQueue(10);
        CaptureEngine engine = CaptureEngine.createDefault(queue, 10);
        OkHttpCaptureInterceptor interceptor =
                new OkHttpCaptureInterceptor(engine, new TrafficTapeProperties());
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(interceptor).build();

        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(201)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"id\":\"9\"}"));
            server.start();
            client.newCall(new Request.Builder()
                            .url(server.url("/ledger"))
                            .post(RequestBody.create("{\"sku\":\"abc\"}", MediaType.get("application/json")))
                            .build())
                    .execute()
                    .close();
        }

        assertThat(queue.size()).isEqualTo(1);
        HttpTransaction tx = queue.drain(1).get(0);
        assertThat(tx.method()).isEqualTo("POST");
        assertThat(tx.path()).isEqualTo("/ledger");
        assertThat(tx.response().status()).isEqualTo(201);
        assertThat(tx.request().body().body().toString()).contains("sku");
        assertThat(tx.response().body().body().toString()).contains("id");
    }
}
