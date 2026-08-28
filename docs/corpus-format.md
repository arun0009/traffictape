# Corpus format

Schema version: `1`. Machine-readable contract: [`schema/http-transaction-v1.json`](../schema/http-transaction-v1.json). CLI `generate` skips events whose `schemaVersion` is not `1`.

```text
{output}/
  README.md
  metadata.json
  statistics.json
  gaps.json
  fanout.json
  events/
    events-000001.jsonl.gz
```

One JSON object per line. Gzip. Not one file per request.

## metadata.json

```json
{
  "schemaVersion": "1",
  "recorder": "traffictape",
  "recorderVersion": "0.5.0",
  "serviceName": "payments-api",
  "environment": "qa",
  "captureStart": "2026-08-26T20:00:00Z",
  "captureEnd": "2026-08-26T22:14:00Z",
  "totalObservedRequests": 2841293,
  "totalCapturedEvents": 4821,
  "totalDroppedEvents": 0,
  "totalLostEvents": 0,
  "writeErrors": 0,
  "captureReady": false,
  "lastNewScenarioAt": "2026-08-26T21:40:00Z"
}
```

## statistics.json

Index of every observed endpoint and scenario, including those that stopped capturing bodies.

- `endpoints[]` / `scenarios[]` — count, status histogram, capturedExamples
- `captureReady` / `lastNewScenarioAt` / `plateauAfterSeconds`
- `gaps[]` — ranked; `bodiesComplete` = `capturedExamples >= min(count, N)`
- `fanout[]` — typical outbound hop sequences per inbound scenario

`gaps.json` and `fanout.json` are the same arrays as standalone files. `README.md` is the read-me.

## HTTP_TRANSACTION event

```json
{
  "schemaVersion": "1",
  "eventType": "HTTP_TRANSACTION",
  "direction": "INBOUND",
  "timestamp": "2026-08-26T22:00:00Z",
  "correlation": {
    "exchangeId": "abc",
    "outboundCount": 2,
    "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
    "correlationId": "corr-1"
  },
  "method": "POST",
  "route": "/orders",
  "path": "/orders",
  "query": {},
  "fingerprints": {
    "endpoint": { "id": "…", "label": "INBOUND POST /orders" },
    "scenario": { "id": "…", "label": "INBOUND POST /orders shape={sku:string} resp=201" }
  },
  "requestShape": "{sku:string}",
  "responseCharacteristic": "201",
  "latencyMs": 42,
  "request": {
    "headers": { "Content-Type": ["application/json"] },
    "contentType": "application/json",
    "body": { "encoding": "JSON", "body": { "sku": "abc" }, "truncated": false, "sizeBytes": 14, "capturedBytes": 14 }
  },
  "response": {
    "status": 201,
    "headers": {},
    "contentType": "application/json",
    "body": { "encoding": "JSON", "body": { "id": "9" }, "truncated": false, "sizeBytes": 10, "capturedBytes": 10 }
  }
}
```

Outbound events set `direction: OUTBOUND`, `destination`, `correlation.parentExchangeId`, `correlation.sequence`. They omit `exchangeId` on the child (the inbound id is `parentExchangeId`).

## Bodies

| encoding | body field |
|---|---|
| `JSON` | parsed, redacted JSON |
| `TEXT` | string |
| `EMPTY` | `null` |
| `OMITTED` | `null` (binary / multipart / excluded content type) |

Never raw binary in JSON. `truncated: true` when over `max-*-bytes`. Unparseable truncated JSON is omitted but still flagged `truncated: true`.

## Reconstructing a scenario for mocks

Prefer `fanout.json`. From events: group outbound by `parentExchangeId`, sort by `sequence`.
