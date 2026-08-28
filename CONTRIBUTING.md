# Contributing

TrafficTape records HTTP from a running Spring Boot app. A CLI turns that tape into WireMock stubs. Spring Boot is the first adapter, not the product.

## Principles

1. The event JSON is the stable contract. Treat field renames as breaking.
2. Capture errors never fail the application request.
3. Default to omission: never capture secrets or binary/multipart bodies.
4. The CLI emits WireMock (default), a test plan, and a JUnit replay skeleton. It does not generate Karate.

## Modules

Published:

- `traffictape-core` — transaction model, fingerprinting, sampling, redaction, `CaptureSink`, gzip JSONL file writer, JSON-line logger
- `traffictape-spring-boot` — Spring MVC and JAX-RS/Jersey inbound; RestClient / RestTemplate / WebClient / OkHttp / JAX-RS `Client` outbound
- `traffictape-cli` — offline `generate`

A non-Spring adapter implements `CaptureSink` against `traffictape-core` only. Shipping the tape (volume, log driver, object storage you already created) is infra, not a module here.

Not published, under `tests/`:

- `traffictape-integration-tests` — end-to-end capture-then-generate test
- `traffictape-overhead-tests` — request-thread overhead and sink isolation

## Extending

Core should stay small. An extension is one SPI plus a `@Bean`.

```java
@Bean
CaptureSink mySink() {
    return batch -> { /* your store */ };
}
```

Same pattern for `Redactor` and `PathNormalizer`. `Fingerprinter`, `Sampler`, and `CaptureMetrics` are also replaceable; most users never do. Do not add framework types to `traffictape-core`.

Do not add more sinks to this repository. Unknown output is a `@Bean CaptureSink`. Unknown HTTP clients call `CaptureEngine.record`. Creating buckets, log groups, or IAM is infra, not this library.

See [docs/architecture.md](docs/architecture.md).

## Pull requests

- Java 17 baseline. CI builds on 17 and 21
- Spring Boot 3.x. Anything requiring Boot 3.2+ (for example `RestClient`) goes behind `@ConditionalOnClass`
- `mvn test` must pass
- Do not add Kafka, Redis, or a capture REST API
