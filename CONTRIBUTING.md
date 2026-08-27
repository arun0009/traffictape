# Contributing

TrafficTape records HTTP from a running Spring Boot app. A CLI turns that recording into WireMock and Mountebank stubs. Spring Boot is the first adapter, not the product.

## Principles

1. The event JSON is the stable contract. Treat field renames as breaking.
2. Capture errors never fail the application request.
3. Default to omission: never capture secrets or binary/multipart bodies.
4. The CLI emits mock *definitions* and a test plan as data. It does not generate Karate or JUnit source.
5. New language adapters (Go, Node, …) should produce the same `HttpTransaction` JSON.

## Modules

Published:

- `traffictape-core` — transaction model, fingerprinting, sampling, redaction, `CaptureSink`, gzip JSONL file writer
- `traffictape-spring-boot` — Spring MVC / RestClient / RestTemplate / WebClient / OkHttp adapter
- `traffictape-sink-s3` — S3 writer (one prefix per task; for Fargate)
- `traffictape-sink-cloudwatch` — CloudWatch Logs (batched `PutLogEvents` + `STATISTICS`)
- `traffictape-cli` — offline `generate` to WireMock / Mountebank / test-plan.json

Adding a sink is enough on its own; each one brings the adapter with it.

Not published, under `tests/`:

- `traffictape-integration-tests` — end-to-end capture-then-generate test, and the demo app
- `traffictape-overhead-tests` — request-thread overhead and sink isolation, plus JMH

## Extending

Core should stay small. An extension is one SPI plus a `@Bean` (or a new language adapter that only calls `CaptureEngine.record`).

```java
@Bean
CaptureSink mySink() {
    return batch -> { /* S3, GCS, Kafka, … */ };
}
```

Same pattern for `Fingerprinter`, `Sampler`, `CaptureMetrics`, `Redactor`, and `PathNormalizer`. File/Spring defaults are skipped if you already provide a bean (`@ConditionalOnMissingBean`). Do not add framework types to `traffictape-core`.

Do not add more sinks to this repository (file, S3, CloudWatch are the set). Unknown output is a `@Bean CaptureSink`. Unknown HTTP clients call `CaptureEngine.record`.

S3 and CloudWatch sinks have Floci Testcontainers ITs (`*FlociIT`). They skip if Docker is not running.

See [docs/architecture.md](docs/architecture.md).

## Pull requests

- Java 17 baseline. Do not use language or API features newer than 17; CI builds on 17 and 21
- Spring Boot 3.x. Anything requiring Boot 3.2+ (for example `RestClient`) goes behind `@ConditionalOnClass`
- `mvn test` must pass
- Do not add Kafka, Redis, or a capture REST API
