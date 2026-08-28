package io.traffictape.capture;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class DestinationHostsTest {

    @Test
    void omitsDefaultHttpAndHttpsPorts() {
        assertThat(DestinationHosts.hostPort(URI.create("https://api.example:443/v1"))).isEqualTo("api.example");
        assertThat(DestinationHosts.hostPort(URI.create("http://api.example:80/v1"))).isEqualTo("api.example");
        assertThat(DestinationHosts.hostPort(URI.create("http://inventory.internal:8080/sku")))
                .isEqualTo("inventory.internal:8080");
        assertThat(DestinationHosts.hostPort(URI.create("https://api.example/v1"))).isEqualTo("api.example");
        assertThat(DestinationHosts.hostPort(null)).isNull();
    }
}
