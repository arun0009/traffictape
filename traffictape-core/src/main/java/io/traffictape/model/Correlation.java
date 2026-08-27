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
    public static Correlation inbound(String exchangeId, String traceId, String spanId, String correlationId) {
        return new Correlation(exchangeId, null, null, null, traceId, spanId, correlationId);
    }

    public Correlation withOutboundCount(int count) {
        return new Correlation(exchangeId, parentExchangeId, sequence, count, traceId, spanId, correlationId);
    }

    public Correlation asOutboundChild(int sequenceNumber) {
        return new Correlation(null, exchangeId, sequenceNumber, null, traceId, spanId, correlationId);
    }
}
