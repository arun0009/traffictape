package io.traffictape.spring.inbound.jersey;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JerseyPathsTest {

    @Test
    void joinsClassAndMethodPaths() throws Exception {
        String route = JerseyPaths.template(Orders.class, Orders.class.getMethod("get", String.class));
        assertThat(route).isEqualTo("/orders/{id}");
    }

    @Test
    void classOnly() throws Exception {
        String route = JerseyPaths.template(Health.class, Health.class.getMethod("ok"));
        assertThat(route).isEqualTo("/health");
    }

    @Path("/orders")
    static class Orders {
        @GET
        @Path("{id}")
        public String get(String id) {
            return id;
        }
    }

    @Path("/health")
    static class Health {
        @GET
        public String ok() {
            return "ok";
        }
    }
}
