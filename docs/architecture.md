# Architecture

TrafficTape records HTTP into files you can copy around (the tape). Spring Boot is the shipping adapter.

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
                    files  /  logger traffictape.tape  /  @Bean CaptureSink
                                    │
                                    ▼
                    CLI → WireMock + test-plan.json + JUnit skeleton
```

The worker batches: **1000 events**, **50 MB**, or **30s** (`traffictape.flush`). A sink never sees one event on the request thread. Files are the tape `generate` reads. `output.console=true` writes the same JSON as one log line per event. The CLI also reads a `.jsonl` dump.

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

```java
@Bean
CaptureSink mySink() {
    return batch -> { /* your store */ };
}
```

`@ConditionalOnMissingBean` skips the default. `ObjectStoreCaptureSink` writes the same file tree through a put callback. A new runtime adapter builds `ObservedExchange` and calls `record`. Do not add framework types to `traffictape-core`.

## Fail-open

Every adapter and `CaptureEngine.record` swallows capture failures. A full queue drops the event and refunds the sampler slot. After retries, lost batches increment `lostEvents` (actuator + `metadata.json`).

## Disabled mode

`traffictape.enabled=false` (default) skips auto-configuration: no filter, no queue, no worker, no tape.

## Exchange graph

Not stored as a nested document. Reconstruct from events:

- inbound: `correlation.exchangeId`, `correlation.outboundCount`
- outbound: `correlation.parentExchangeId`, `correlation.sequence`

Offline: events where `exchangeId=X` or `parentExchangeId=X`, order outbound by `sequence`. `fanout.json` is the typical sequence.
