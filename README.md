<div align="center">

# TrafficTape

[![Build](https://img.shields.io/github/actions/workflow/status/arun0009/traffictape/maven.yml?branch=main&label=Build)](https://github.com/arun0009/traffictape/actions)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.arun0009/traffictape-spring-boot?label=Maven%20Central)](https://central.sonatype.com/search?q=io.github.arun0009%20traffictape)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://adoptium.net)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)

**Record real HTTP. Keep the tape. Throw the recorder away.**

A privacy-aware HTTP flight recorder for Spring Boot. Drop it into QA, capture representative inbound and outbound traffic, copy the files, remove the dependency.

<img src="./traffictape.png" alt="TrafficTape" width="280">

</div>

TrafficTape is not a request logger and not a test generator. It writes a portable **traffic corpus** — sampled, redacted, fingerprinted — so you (or an offline tool) can see which API *scenarios* actually happened.

```yaml
traffictape:
  enabled: true
  max-examples-per-scenario: 50   # per scenario, not per URL
  plateau-after: 6h
  output:
    directory: /tmp/traffic-tape
```

Disabled by default. Fail-open. Bounded. Asynchronous. File, CloudWatch, and S3 are the shipped sinks; anything else is a `@Bean CaptureSink`.

## Why

Karate (or any suite) covers the endpoints you remembered to write. Real QA traffic shows the rest: the `PATCH` that sometimes sends `{status}`, sometimes `{owner}`, the `404` that is a real client path, the two outbound calls that belong to one inbound request.

TrafficTape keeps **representative examples**, not every request. Statistics keep counting after bodies stop.

## What you get

| | |
|---|---|
| **Two fingerprints** | *Endpoint* = method + route + query names. *Scenario* = endpoint + request shape + response characteristic. |
| **Exchange graph** | Inbound `exchangeId` + outbound `parentExchangeId` / `sequence`. One user request and the calls it made. |
| **Safe defaults** | Secrets, cookies, `/health`, `/actuator/**`, multipart, and binary are omitted. Denylisted fields are redacted in JSON, XML, and form bodies. |
| **Fail-open** | Capture never fails the application. A full queue drops the event. |
| **Escape hatch** | `@Bean` of `CaptureSink`, `Sampler`, `Fingerprinter`, `CaptureMetrics`, `Redactor`, or `PathNormalizer`. Unknown HTTP stack: `captureEngine.record(ObservedExchange…)`. |

```text
PATCH /assets/{id}                         ← endpoint
  PATCH + {status:string} + 200            ← scenario
  PATCH + {owner:string} + 200             ← scenario

INBOUND POST /orders          exchangeId=abc  outboundCount=2
OUTBOUND GET  /inventory/{id} parent=abc sequence=1
OUTBOUND POST /ledger         parent=abc sequence=2
```

That graph is what lets an offline tool emit **one regression test + N mocks**.

## Install

Java **17+**, Spring Boot **3.x**, Spring MVC (servlet).

```xml
<dependency>
    <groupId>io.github.arun0009</groupId>
    <artifactId>traffictape-spring-boot</artifactId>
    <version>${traffictape.version}</version>
</dependency>
```

Inject `RestClient.Builder` / `RestTemplateBuilder` / `WebClient.Builder` so outbound interceptors apply. `RestClient.create()` and `new OkHttpClient()` bypass capture.

```yaml
traffictape:
  enabled: false   # turn on only in QA
  output:
    directory: /tmp/traffic-tape
  capture:
    exclude:
      routes: [/health, /actuator/**]
      content-types: [multipart/form-data, application/octet-stream]
      request-headers:
        X-Smoke-Test: ["*"]       # synthetic traffic on real endpoints
```

Restart after changing `enabled` (v0.1 is startup-config only). Full options: [configuration](docs/configuration.md).

**Start with your test suite** if you do not want to deploy yet: enable TrafficTape in test scope, run `mvn test`, then generate mocks. See [capture from tests](docs/capture-from-tests.md).

QA capture is the path to scenarios your tests miss. Run for a couple of days (nightly and weekend jobs), then stop when `/actuator/traffictape` reports `ready` — or when you are done — copy the corpus, and remove the dependency. TrafficTape is not meant to stay in production.

```console
$ curl -s localhost:8080/actuator/traffictape
{"ready":false,"plateauReached":false,"sinkDisabled":false,...}
```

Expose it with `management.endpoints.web.exposure.include: [health, traffictape]`. Route templates and counts only; no bodies.

Fargate without S3: add `traffictape-sink-cloudwatch`. A bucket is allowed: add `traffictape-sink-s3`. Details: [configuration](docs/configuration.md).

## Generate mocks

```bash
java -jar traffictape-cli-${traffictape.version}-all.jar generate --corpus /tmp/traffic-tape --out ./out
```

Outbound scenarios become WireMock / Mountebank stubs. Inbound scenarios become `test-plan.json` cases, each naming the outbound stubs the same request caused. [Generate](docs/generate.md).

## Extending

```java
@Bean
CaptureSink mySink() {
    return batch -> { /* your store */ };
}
```

Defaults back off via `@ConditionalOnMissingBean`. Custom ID shapes: extend `DefaultPathNormalizer`. Value-shaped PII: extend `DefaultRedactor`.

## v0.1 limits

- Inbound capture is **Spring MVC (servlet) only**. WebFlux inbound is not supported.
- **Async servlet** dispatches lose inbound/outbound correlation.
- WebClient **request** bodies are not rematerialized (responses are).
- Plain-text bodies cannot be field-redacted; unparseable JSON is omitted.
- Sampler budget is per JVM; the CLI deduplicates by scenario fingerprint offline.
- The CLI emits mock definitions and a test plan as **data**, not Karate or JUnit source.

## Docs

[Architecture](docs/architecture.md) · [Capture from tests](docs/capture-from-tests.md) · [Corpus format](docs/corpus-format.md) · [Generate mocks](docs/generate.md) · [Configuration](docs/configuration.md) · [Sampling](docs/sampling.md) · [Redaction](docs/redaction.md) · [AI workflow](docs/ai-workflow.md)

## Contributing

Issues and pull requests welcome at [github.com/arun0009/traffictape](https://github.com/arun0009/traffictape). See [CONTRIBUTING](CONTRIBUTING.md). Released under the [MIT License](LICENSE).
