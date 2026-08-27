# Architecture

TrafficTape records real HTTP behavior into a portable corpus. Spring Boot is an adapter.

```text
                    TrafficTape
                        │
                 HTTP Capture Model
                 (traffictape-core)
                        │
                ┌───────┴────────┐
                │                │
          Java/Spring         future:
           adapter            Go / Node
                │                │
                └───────┬────────┘
                        ▼
                  Traffic Corpus
                        │
              ┌─────────┼─────────┐
              ▼         ▼         ▼
           Claude     Replay    Analysis
              │
          ┌───┴────┐
          ▼        ▼
       Karate   WireMock
```

## Capture path (Spring v0.2)

```text
inbound Filter / RestClient, RestTemplate, WebClient, OkHttp interceptors
        │
        ▼
  CaptureEngine.record(ObservedExchange)
        │
        ├── policy (method/route/content-type/destination)
        ├── endpoint + scenario fingerprints
        ├── statistics (always) + fan-out graph
        ├── sampler (per scenario key)
        ├── redact
        └── queue.offer        ← never put(), never block
                │
                ▼
         AsyncCaptureWorker
                │
                ▼
            CaptureSink.write(CaptureBatch)
                │
     ┌──────────┼──────────────┐
     ▼          ▼              ▼
   File       S3          CloudWatch
  (JSONL.gz) (objects)     (logs)
```

The worker already batches: **1000 events**, **50&nbsp;MB**, or **30s** (configurable `traffictape.flush`). A sink never sees one event on the request thread. File is the reference corpus. CloudWatch is the Fargate path when S3 is blocked. S3 is the file tree when a bucket is allowed.

## Extending (one SPI, one `@Bean`)

Core is the engine. Adding a backend or strategy should not touch `CaptureEngine`.

| Extension | Implement | Wire |
|---|---|---|
| S3 / CloudWatch / anything else | `CaptureSink` | `@Bean CaptureSink` |
| Custom scenario identity | `Fingerprinter` | `@Bean Fingerprinter` |
| Custom sampling | `Sampler` | `@Bean Sampler` |
| Metrics backend | `CaptureMetrics` | `@Bean CaptureMetrics` |
| PII / value-shaped detection | `Redactor` (usually extend `DefaultRedactor`) | `@Bean Redactor` |
| Org-specific ID shapes in URLs | `PathNormalizer` (usually extend `DefaultPathNormalizer`) | `@Bean PathNormalizer` |
| Quarkus / servlet / Go | build `ObservedExchange`, call `record` | adapter module |

Spring defaults back off via `@ConditionalOnMissingBean`. User beans win; core does not change.

A new runtime adapter is:

```java
captureEngine.record(ObservedExchange.builder()
        .direction(Direction.INBOUND)
        .method(method)
        .path(path)
        .status(status)
        .build());
```

## Fail-open

Every adapter and `CaptureEngine.record` swallows capture failures. A full queue drops the event. A dead worker or throwing sink does not fail the application request.

## Disabled mode

`traffictape.enabled=false` (default) skips auto-configuration: no filter, no queue, no worker, no files.

## Exchange graph

Not stored as a nested document. Reconstruct from events:

- inbound: `correlation.exchangeId`, `correlation.outboundCount`
- outbound: `correlation.parentExchangeId`, `correlation.sequence`

Offline: events where `exchangeId=X` or `parentExchangeId=X`, order outbound by `sequence`. `fanout.json` is the typical sequence.
