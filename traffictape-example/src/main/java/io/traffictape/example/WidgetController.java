package io.traffictape.example;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Map;

@RestController
public class WidgetController {

    private final RestClient restClient;

    public WidgetController(RestClient restClient) {
        this.restClient = restClient;
    }

    @GetMapping("/widgets/{id}")
    Map<String, Object> get(@PathVariable String id) {
        Map<?, ?> inventory = restClient.get()
                .uri(base() + "/external/inventory/{id}", id)
                .retrieve()
                .body(Map.class);
        return Map.of("id", id, "name", "widget-" + id, "inventory", inventory);
    }

    @PostMapping("/widgets")
    ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        restClient.post()
                .uri(base() + "/external/catalog")
                .body(body)
                .retrieve()
                .toBodilessEntity();
        return ResponseEntity.status(201).body(Map.of("id", "w-1", "created", true));
    }

    @PatchMapping("/widgets/{id}")
    Map<String, Object> patch(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return Map.of("id", id, "patched", body);
    }

    @GetMapping("/external/inventory/{id}")
    Map<String, Object> inventory(@PathVariable String id) {
        return Map.of("sku", id, "qty", 7);
    }

    @PostMapping("/external/catalog")
    Map<String, Object> catalog(@RequestBody Map<String, Object> body) {
        return Map.of("accepted", true);
    }

    private static String base() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
