# Corpus format

Schema version: `1`

The corpus is the public contract. Language adapters must produce the same JSON.

```text
{output}/
  FOR_CLAUDE.md
  metadata.json
  statistics.json
  gaps.json
  fanout.json
  events/
    events-000001.jsonl.gz
    events-000002.jsonl.gz
```

One JSON object per line. Gzip. Not one file per request.

## metadata.json

```json
{
  "schemaVersion": "1",
  "recorder": "traffictape",
  "recorderVersion": "0.2.0",
  "serviceName": "payments-api",
  "environment": "qa",
  "captureStart": "2026-08-26T20:00:00Z",
  "captureEnd": "2026-08-26T22:14:00Z",
  "totalObservedRequests": 2841293,
  "totalCapturedEvents": 4821,
  "totalDroppedEvents": 0,
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

`gaps.json` and `fanout.json` are the same arrays as standalone files. `FOR_CLAUDE.md` is the read-me. CloudWatch puts truncated `gaps`/`fanout` on each `STATISTICS` event.

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

Never raw binary in JSON. `truncated: true` when over `max-*-bytes`.

## Reconstructing a scenario for mocks

Prefer `fanout.json` (or `fanout` on `STATISTICS`). From events: group outbound by `parentExchangeId`, sort by `sequence`.
