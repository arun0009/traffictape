# Architecture

TrafficTape records HTTP into a portable corpus. Spring Boot is the shipping adapter.

```text
  Spring MVC / Jersey / RestClient / RestTemplate / WebClient / OkHttp / JAX-RS Client
                                    │
                                    ▼
                         CaptureEngine.record
                                    │
              policy → fingerprint → stats → sampler → redact → queue
                                    │
                          AsyncCaptureWorker
                                    │
                    File (canonical) / S3 / CloudWatch
                                    │
                                    ▼
                    CLI → WireMock + test-plan.json + JUnit skeleton
```

The worker batches: **1000 events**, **50 MB**, or **30s** (`traffictape.flush`). A sink never sees one event on the request thread. File is the reference corpus. S3 is the same tree in a bucket. CloudWatch is a JSON-line transport when a bucket is blocked; the CLI can still read a dump.

## Capture path

```text
adapter → ObservedExchange → CaptureEngine.record()  (never throws)
        ├── policy
        ├── fingerprints + statistics (always)
        ├── sampler (bodies only; a failed enqueue refunds the slot)
        ├── redact
        └── queue.offer        ← never put(), never block
                │
                ▼
         worker → CaptureSink.write(CaptureBatch)
                   (up to 3 attempts; then lostEvents++)
```

## Extending

The SPIs most people replace are **`CaptureSink`** and **`Redactor`**. Also overridable: `Fingerprinter`, `Sampler`, `PathNormalizer`, `CaptureMetrics`.

Wire any of them as a `@Bean`. `@ConditionalOnMissingBean` skips the default.

A new runtime adapter builds `ObservedExchange` and calls `record`. Do not add framework types to `traffictape-core`.

## Fail-open

Every adapter and `CaptureEngine.record` swallows capture failures. A full queue drops the event and refunds the sampler slot. After retries, lost batches increment `lostEvents` (actuator + `metadata.json`).

## Disabled mode

`traffictape.enabled=false` (default) skips auto-configuration: no filter, no queue, no worker, no files.

## Exchange graph

Not stored as a nested document. Reconstruct from events:

- inbound: `correlation.exchangeId`, `correlation.outboundCount`
- outbound: `correlation.parentExchangeId`, `correlation.sequence`

Offline: events where `exchangeId=X` or `parentExchangeId=X`, order outbound by `sequence`. `fanout.json` is the typical sequence.
