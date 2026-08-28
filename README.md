<div align="center">

# TrafficTape

[![Build](https://img.shields.io/github/actions/workflow/status/arun0009/traffictape/maven.yml?branch=main&label=Build)](https://github.com/arun0009/traffictape/actions)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.arun0009/traffictape-spring-boot?label=Maven%20Central)](https://central.sonatype.com/search?q=io.github.arun0009%20traffictape)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://adoptium.net)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)

**Record real HTTP. Keep the tape. Throw the recorder away.**

<img src="./traffictape.png" alt="TrafficTape" width="280">

</div>

A Spring Boot recorder and an offline CLI. Add it in **test scope**, run the suite you already have, generate WireMock stubs. Java 17+, Spring Boot 3.x. Capture is off until you turn it on, and it never fails the application request.

```xml
<dependency>
    <groupId>io.github.arun0009</groupId>
    <artifactId>traffictape-spring-boot</artifactId>
    <version>${traffictape.version}</version>
    <scope>test</scope>
</dependency>
```

```yaml
# src/test/resources/application.yml
traffictape:
  enabled: true
  output:
    directory: target/traffic-tape
```

```bash
mvn test
java -jar traffictape-cli-${traffictape.version}-all.jar generate \
  --tape target/traffic-tape --out ./out
```

Pin `${traffictape.version}` to the Maven Central badge. Grab the CLI `-all` jar from the [latest release](https://github.com/arun0009/traffictape/releases/latest). Test-scope loop in detail: [Capture from tests](docs/capture-from-tests.md).

## Why

Your tests cover the paths you remembered. Real traffic shows the rest: a `PATCH` that sometimes sends `{status}` and sometimes `{owner}`, a `404` a real client hits, two backend calls that belong to one request.

TrafficTape groups those into *scenarios* (same route, different request shape or status) and keeps a few examples of each, not every request.

```text
PATCH /assets/{id}
  {status} → 200
  {owner}  → 200

POST /orders
  → GET  /inventory/{id}
  → POST /ledger
```

First 10 examples per scenario (configurable). Counts continue after bodies stop.

## Capture in QA

Same artifact, without `test` scope. Inbound: Spring MVC **or** JAX-RS/Jersey on a servlet container. Outbound: an injected `RestClient.Builder`, `RestTemplateBuilder`, `WebClient.Builder`, `OkHttpClient` bean, or JAX-RS `Client` bean. A client you construct yourself is not recorded.

```yaml
traffictape:
  enabled: true
  output:
    directory: /tmp/traffic-tape
```

Leave it on until `/actuator/traffictape` reports `ready: true`, copy the tape, remove the dependency. Expose the endpoint with `management.endpoints.web.exposure.include: [health, traffictape]`. Restart after changing `enabled`. [Configuration](docs/configuration.md).

**Where the tape goes**

- **Files (default)** — gzip JSONL under `output.directory`. Copy the folder off the box.
- **Log driver** — `output.console: true` writes JSON lines to logger `traffictape.tape`.
- **Anything else** — a `@Bean CaptureSink`.

This library writes the tape. It does not create buckets, log groups, or IAM.

## Generate

```bash
java -jar traffictape-cli-${traffictape.version}-all.jar generate \
  --tape /tmp/traffic-tape --out ./out
```

Writes WireMock mappings (default), `test-plan.json`, and a JUnit 5 replay skeleton. Mountebank: `--format mountebank`. [Generate](docs/generate.md).

## Extending

A `@Bean` of `CaptureSink` or `Redactor` replaces the default. `ObjectStoreCaptureSink` writes the same file tree through a put callback if you already have a store. Unknown HTTP clients call `CaptureEngine.record`.

## Limits

- Inbound: Spring MVC or JAX-RS/Jersey as a servlet. Not WebFlux.
- Async servlet (`DeferredResult`, `Callable`) does not link outbound calls to the parent request.
- SSE responses are metadata only (no body tee).
- Plain text is not field-redacted. Broken JSON is dropped, not stored raw.

## Docs

[Architecture](docs/architecture.md) · [Capture from tests](docs/capture-from-tests.md) · [Tape format](docs/tape-format.md) · [Generate](docs/generate.md) · [Configuration](docs/configuration.md) · [Redaction](docs/redaction.md)

## Contributing

Issues and pull requests: [github.com/arun0009/traffictape](https://github.com/arun0009/traffictape). See [CONTRIBUTING](CONTRIBUTING.md). [MIT License](LICENSE).
