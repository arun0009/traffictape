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

Add this to a Spring Boot service in QA. It records inbound requests and the outbound calls they make, redacts secrets, and writes a small set of examples to disk. Copy the files, generate WireMock or Mountebank stubs, then remove the dependency.

It is not a logger and it does not stay in production.

```yaml
traffictape:
  enabled: true
  max-examples-per-scenario: 50
  output:
    directory: /tmp/traffic-tape
```

Off by default. If capture breaks, the app still serves traffic.

## Why

Your tests cover the paths you remembered. QA traffic shows the rest: a `PATCH` that sometimes sends `{status}` and sometimes `{owner}`, a `404` a real client hits, two backend calls that belong to one request.

TrafficTape groups those into *scenarios* (same route, different request shape or status) and keeps a few examples of each, not every request.

```text
PATCH /assets/{id}
  {status} → 200
  {owner}  → 200

POST /orders
  → GET  /inventory/{id}
  → POST /ledger
```

## Install

Java 17+, Spring Boot 3.x. Inbound: Spring MVC **or** JAX-RS/Jersey on a servlet container. Same artifact.

```xml
<dependency>
    <groupId>io.github.arun0009</groupId>
    <artifactId>traffictape-spring-boot</artifactId>
    <version>${traffictape.version}</version>
</dependency>
```

Outbound capture needs an injected `RestClient.Builder`, `RestTemplateBuilder`, `WebClient.Builder`, an `OkHttpClient` Spring bean, or a JAX-RS `Client` Spring bean. `RestClient.create()`, `ClientBuilder.newClient()`, and a client you construct yourself are not recorded.

Add **one** of these. A sink includes the starter; do not add both.

| Where the tape goes | Artifact |
|---|---|
| Local disk | `traffictape-spring-boot` |
| S3 | `traffictape-sink-s3` |
| CloudWatch Logs | `traffictape-sink-cloudwatch` |

```yaml
traffictape:
  enabled: false          # turn on in QA only
  output:
    directory: /tmp/traffic-tape
```

Restart after changing `enabled`. [Configuration](docs/configuration.md).

No QA environment yet? Enable it in test scope, run `mvn test`, then generate stubs. [Capture from tests](docs/capture-from-tests.md).

In QA, leave it on for a couple of days (nightly jobs included). Then:

```console
curl -s localhost:8080/actuator/traffictape
```

`ready: true` means no new behaviour for a while. Copy `/tmp/traffic-tape` and take the dependency out. Expose the endpoint with `management.endpoints.web.exposure.include: [health, traffictape]`.

Fargate: [CloudWatch](docs/configuration.md#fargate--cloudwatch) or [S3](docs/configuration.md#fargate--s3).

## Generate stubs

Grab the CLI from the [latest release](https://github.com/arun0009/traffictape/releases/latest).

```bash
java -jar traffictape-cli-${traffictape.version}-all.jar generate \
  --corpus /tmp/traffic-tape --out ./out
```

Outbound calls become WireMock / Mountebank stubs. Inbound requests become rows in `test-plan.json` that name those stubs. [Generate](docs/generate.md).

Need a different store, redaction rule, or URL id shape? Expose a `@Bean` of `CaptureSink`, `Redactor`, or `PathNormalizer`.

## Limits

- Inbound: Spring MVC, or JAX-RS/Jersey as a servlet. Not WebFlux.
- Async servlet (`DeferredResult`, `Callable`) does not link outbound calls to the parent request.
- WebClient request bodies are not captured (responses are). JAX-RS client request entities are captured only when they are already a `String` or `byte[]`.
- Plain text is not field-redacted. Broken JSON is dropped, not stored raw.
- The CLI writes stub files and a test plan. It does not generate Karate or JUnit.

## Docs

[Architecture](docs/architecture.md) · [Capture from tests](docs/capture-from-tests.md) · [File format](docs/corpus-format.md) · [Generate](docs/generate.md) · [Configuration](docs/configuration.md) · [Sampling](docs/sampling.md) · [Redaction](docs/redaction.md)

## Contributing

Issues and pull requests: [github.com/arun0009/traffictape](https://github.com/arun0009/traffictape). See [CONTRIBUTING](CONTRIBUTING.md). [MIT License](LICENSE).
