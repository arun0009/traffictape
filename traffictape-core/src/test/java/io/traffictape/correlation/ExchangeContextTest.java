package io.traffictape.correlation;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExchangeContextTest {

    @Test
    void parsesW3cTraceparent() {
        ExchangeContext ctx = ExchangeContext.open(Map.of(
                "traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                "x-correlation-id", "corr-1"));
        assertThat(ctx.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(ctx.spanId()).isEqualTo("00f067aa0ba902b7");
        assertThat(ctx.correlationId()).isEqualTo("corr-1");
        assertThat(ctx.exchangeId()).isNotBlank();
    }

    @Test
    void outboundSequenceIncrements() {
        ExchangeContext ctx = ExchangeContext.open(Map.of());
        assertThat(ctx.nextOutboundSequence()).isEqualTo(1);
        assertThat(ctx.nextOutboundSequence()).isEqualTo(2);
        assertThat(ctx.outboundCount()).isEqualTo(2);
    }
}
