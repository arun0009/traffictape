# Contributing

TrafficTape is a **privacy-aware HTTP traffic corpus and flight recorder**.
Spring Boot is the first capture adapter, not the product identity.

## Principles

1. The corpus schema is the stable contract. Treat field renames as breaking.
2. Capture is fail-open. Application traffic always wins.
3. Default to omission: never capture secrets or binary/multipart bodies.
4. Do not generate Karate, WireMock, or Mountebank in this repository.
5. New language adapters (Go, Node, …) should produce the same `HttpTransaction` JSON.

## Modules

- `traffictape-core` — transaction model, fingerprinting, sampling, redaction, `CaptureSink`
- `traffictape-sink-file` — gzip JSONL writer
- `traffictape-sink-s3` — S3 writer (one prefix per task; for Fargate)
- `traffictape-sink-cloudwatch` — CloudWatch Logs (batched `PutLogEvents` + `STATISTICS`)
- `traffictape-spring-boot` — Spring MVC / RestClient / RestTemplate / WebClient / OkHttp adapter
- `traffictape-example` — demo app
- `traffictape-benchmarks` — overhead checks

## Extending

Core should stay small. An extension is one SPI plus a `@Bean` (or a new language adapter that only calls `CaptureEngine.record`).

```java
@Bean
CaptureSink mySink() {
    return batch -> { /* S3, GCS, Kafka, … */ };
}
```

Same pattern for `Fingerprinter`, `Sampler`, and `CaptureMetrics`. File/Spring defaults back off via `@ConditionalOnMissingBean`. Do not add framework types to `traffictape-core`.

Do not add more sinks to this repository (file, S3, CloudWatch are the set). Unknown output is a `@Bean CaptureSink`. Unknown HTTP clients call `CaptureEngine.record`.

S3 and CloudWatch sinks have Floci Testcontainers ITs (`*FlociIT`). They skip if Docker is not running.

See [docs/architecture.md](docs/architecture.md).

## Pull requests

- Java 21, Spring Boot 3.4
- `mvn test` must pass
- Do not add Kafka, Redis, or a capture REST API
