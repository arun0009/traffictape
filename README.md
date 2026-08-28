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

Add this in **test scope**, run the suite you already have, generate WireMock stubs. No QA environment required.

It is not a logger and it does not stay in production.

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
  --corpus target/traffic-tape --out ./out
```

Off by default in a real app. If capture breaks, the app still serves traffic.

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

## Install (QA / a running service)

Java 17+, Spring Boot 3.x. Inbound: Spring MVC **or** JAX-RS/Jersey on a servlet container. Same artifact without `test` scope.

Outbound capture needs an injected `RestClient.Builder`, `RestTemplateBuilder`, `WebClient.Builder`, an `OkHttpClient` Spring bean, or a JAX-RS `Client` Spring bean. `RestClient.create()`, `ClientBuilder.newClient()`, and a client you construct yourself are not recorded.

Add **one** of these. A sink includes the starter; do not add both.

| Where the tape goes | Artifact |
|---|---|
| Local disk | `traffictape-spring-boot` |
| S3 | `traffictape-sink-s3` |
| CloudWatch Logs | `traffictape-sink-cloudwatch` |

```yaml
traffictape:
  enabled: false
  output:
    directory: /tmp/traffic-tape
```

Restart after changing `enabled`. [Configuration](docs/configuration.md). Test-scope loop: [Capture from tests](docs/capture-from-tests.md).

In QA, leave it on until `/actuator/traffictape` reports `ready: true`, then copy the corpus and remove the dependency. Expose the endpoint with `management.endpoints.web.exposure.include: [health, traffictape]`.

Fargate: [CloudWatch](docs/configuration.md#fargate--cloudwatch) or [S3](docs/configuration.md#fargate--s3). CloudWatch is a JSON-line transport; file and S3 are the canonical corpus tree.

## Generate stubs

Grab the CLI from the [latest release](https://github.com/arun0009/traffictape/releases/latest).

```bash
java -jar traffictape-cli-${traffictape.version}-all.jar generate \
  --corpus /tmp/traffic-tape --out ./out
```

Writes WireMock mappings (default), `test-plan.json`, and a JUnit 5 replay skeleton. Mountebank: `--format mountebank`. [Generate](docs/generate.md).

Need a different store or redaction rule? Expose a `@Bean` of `CaptureSink` or `Redactor`.

## Limits

- Inbound: Spring MVC or JAX-RS/Jersey as a servlet. Not WebFlux.
- Async servlet (`DeferredResult`, `Callable`) does not link outbound calls to the parent request.
- SSE responses are metadata only (no body tee).
- Plain text is not field-redacted. Broken JSON is dropped, not stored raw.

## Docs

[Architecture](docs/architecture.md) · [Capture from tests](docs/capture-from-tests.md) · [File format](docs/corpus-format.md) · [Generate](docs/generate.md) · [Configuration](docs/configuration.md) · [Redaction](docs/redaction.md)

## Contributing

Issues and pull requests: [github.com/arun0009/traffictape](https://github.com/arun0009/traffictape). See [CONTRIBUTING](CONTRIBUTING.md). [MIT License](LICENSE).
