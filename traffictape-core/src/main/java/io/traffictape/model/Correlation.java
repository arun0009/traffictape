package io.traffictape.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Identifiers that let an offline consumer reconstruct an inbound request
 * together with the outbound calls it caused:
 *
 * <pre>
 *   exchangeId=abc
 *     INBOUND  POST /orders
 *     OUTBOUND sequence=1 GET  /inventory/{sku}
 *     OUTBOUND sequence=2 POST /ledger
 *     inbound response 201
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Correlation(
        String exchangeId,
        String parentExchangeId,
        Integer sequence,
        Integer outboundCount,
        String traceId,
        String spanId,
        String correlationId
) {
    public static Correlation inbound(
            String exchangeId, Integer outboundCount, String traceId, String spanId, String correlationId) {
        return new Correlation(exchangeId, null, null, outboundCount, traceId, spanId, correlationId);
    }

    public static Correlation outbound(
            String parentExchangeId, Integer sequence, String traceId, String spanId, String correlationId) {
        return new Correlation(null, parentExchangeId, sequence, null, traceId, spanId, correlationId);
    }
}
