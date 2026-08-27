package io.traffictape.capture;

import io.traffictape.body.BodyCodec;
import io.traffictape.correlation.ExchangeContext;
import io.traffictape.model.BodyCapture;
import io.traffictape.model.Correlation;
import io.traffictape.model.Direction;
import io.traffictape.model.EventType;
import io.traffictape.model.FingerprintPair;
import io.traffictape.model.HttpRequest;
import io.traffictape.model.HttpResponse;
import io.traffictape.model.HttpTransaction;
import io.traffictape.redaction.Redactor;

/**
 * Turns a raw observation into an event. Redaction happens here, before enqueue.
 */
final class HttpTransactionFactory {

    private final BodyCodec bodyCodec;
    private final Redactor redactor;

    HttpTransactionFactory(BodyCodec bodyCodec, Redactor redactor) {
        this.bodyCodec = bodyCodec;
        this.redactor = redactor;
    }

    HttpTransaction create(
            ObservedExchange observed,
            String route,
            String requestShape,
            String responseCharacteristic,
            FingerprintPair pair) {
        BodyCapture requestBody = bodyCodec.decode(
                observed.requestBody(),
                observed.requestContentType(),
                observed.requestTruncated(),
                observed.requestDeclaredSize());
        BodyCapture responseBody = bodyCodec.decode(
                observed.responseBody(),
                observed.responseContentType(),
                observed.responseTruncated(),
                observed.responseDeclaredSize());
        return new HttpTransaction(
                HttpTransaction.SCHEMA_VERSION,
                EventType.HTTP_TRANSACTION,
                observed.direction(),
                observed.timestamp(),
                correlation(observed),
                observed.destination(),
                observed.method() == null ? null : observed.method().toUpperCase(),
                route,
                observed.path(),
                observed.query(),
                pair,
                requestShape,
                responseCharacteristic,
                observed.latencyMs(),
                new HttpRequest(redactor.headers(observed.requestHeaders()), observed.requestContentType(), requestBody),
                new HttpResponse(
                        observed.status(),
                        redactor.headers(observed.responseHeaders()),
                        observed.responseContentType(),
                        responseBody)
        );
    }

    private static Correlation correlation(ObservedExchange observed) {
        ExchangeContext ctx = observed.exchangeContext();
        String trace = ctx == null ? null : ctx.traceId();
        String span = ctx == null ? null : ctx.spanId();
        String corr = ctx == null ? null : ctx.correlationId();
        if (observed.direction() == Direction.OUTBOUND) {
            String parent = ctx == null ? null : ctx.exchangeId();
            return Correlation.outbound(parent, observed.outboundSequence(), trace, span, corr);
        }
        String id = ctx == null ? null : ctx.exchangeId();
        Integer hops = ctx == null ? null : ctx.outboundCount();
        return Correlation.inbound(id, hops, trace, span, corr);
    }
}
