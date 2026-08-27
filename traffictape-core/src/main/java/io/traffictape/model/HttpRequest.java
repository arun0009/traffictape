package io.traffictape.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HttpRequest(
        Map<String, List<String>> headers,
        String contentType,
        BodyCapture body
) {
}
