<div align="center">

# TrafficTape

[![Maven Central](https://img.shields.io/maven-central/v/io.github.arun0009/traffictape-spring-boot?label=Maven%20Central)](https://central.sonatype.com/search?q=io.github.arun0009%20traffictape)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://adoptium.net)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)

**Record real HTTP. Keep a corpus. Throw the recorder away.**

A privacy-aware HTTP flight recorder for Spring Boot. Drop it into QA, capture representative inbound and outbound traffic, copy the files, remove the dependency.

<img src="./traffictape.png" alt="TrafficTape" width="280">

</div>

TrafficTape is not a request logger and not a test generator. It writes a portable **traffic corpus** — sampled, redacted, fingerprinted — so you (or an offline tool) can see which API *scenarios* actually happened.

```yaml
traffictape:
  enabled: true
  max-examples-per-scenario: 50   # 10, 50, or 100 — per scenario, not per URL
  plateau-after: 6h               # captureReady after no new unique scenario this long
  output:
    directory: /tmp/traffic-tape  # local
    # Fargate (no S3): add traffictape-sink-cloudwatch
    # cloudwatch:
    #   log-group: /traffictape/qa/payments-api
```

Disabled by default. Fail-open. Bounded. Asynchronous. **File, CloudWatch, and S3 are the only sinks.** Anything else is a `@Bean`.

## Why

Karate (or any suite) covers the endpoints you remembered to write. Real QA traffic shows the rest: the `PATCH` that sometimes sends `{status}`, sometimes `{owner}`, the `404` that is a real client path, the two outbound calls that belong to one inbound request.

TrafficTape keeps **representative examples**, not every request. Statistics keep counting after bodies stop.

## What you get

| | |
|---|---|
| **Two fingerprints** | *Endpoint* = method + route + query names. *Scenario* = endpoint + request shape + response characteristic. Coverage is not “we hit `PATCH /assets/{id}`.” |
| **Exchange graph** | Inbound `exchangeId` + outbound `parentExchangeId` / `sequence`. Reconstruct one user request and the calls it made. |
| **Safe defaults** | Secrets, cookies, `/health`, `/actuator/**`, multipart, and binary are omitted. Bodies are capped. Denylisted fields are redacted in JSON, XML, and form-urlencoded payloads alike. |
| **Fail-open** | Capture never fails the application. A full queue drops the event. |
| **A corpus, not a dump of every request** | First N examples per *scenario*. `statistics.json` is the index; `gaps.json` and `fanout.json` are the rewrite brief. |
| **Escape hatch** | `@Bean` of `CaptureSink`, `Sampler`, `Fingerprinter`, `CaptureMetrics`, `Redactor`, or `PathNormalizer`. Unknown HTTP stack: `captureEngine.record(ObservedExchange.builder()…)`. |

```text
PATCH /assets/{id}                         ← endpoint
  PATCH + {status:string} + 200            ← scenario
  PATCH + {owner:string} + 200             ← scenario
  PATCH + {metadata:object} + 400          ← scenario
```

```text
INBOUND POST /orders          exchangeId=abc  outboundCount=2
OUTBOUND GET  /inventory/{id} parent=abc sequence=1
OUTBOUND POST /ledger         parent=abc sequence=2
```

That graph is what lets an offline tool emit **one regression test + N mocks**.

## Start with your test suite

Before deploying anything, point TrafficTape at the tests you already have:

```bash
mvn test    # with traffictape enabled in test scope
java -jar traffictape-cli-0.1.0-all.jar generate --corpus target/traffic-tape --out ./out
```

No deployment, no waiting, and nothing to review about customer data on disk — the only traffic is traffic your own tests generated. You get stubs and a test plan in one build, and a baseline that pins current behaviour before a refactor. The corpus is only as good as the suite: [capture from tests](docs/capture-from-tests.md) covers which test styles record what.

## Disposable lifecycle

QA capture is the path to the scenarios your tests *don't* cover.

```text
1. Add traffictape-spring-boot
2. Deploy to QA
3. Enable capture
4. Let real traffic run for **2–3 days** (nightly and weekend jobs)
5. When a scenario hits N, bodies stop for that scenario; new scenarios still record
6. Disable capture when [ready](#knowing-when-to-stop) (or when you are done)
7. Copy the corpus
8. Analyze (you, a script, or an AI) against existing tests + source
9. Remove the dependency
```

TrafficTape is not meant to stay in production.

## Knowing when to stop

With Actuator on the classpath, expose the endpoint and ask:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: [health, traffictape]
```

```console
$ curl -s localhost:8080/actuator/traffictape
{"ready":false,"plateauReached":false,"scenariosMissingExamples":0,
 "uniqueEndpoints":7,"uniqueScenarios":8,"observedRequests":8,
 "capturedEvents":8,"droppedEvents":0,"writeErrors":0,"incomplete":[]}
```

`ready` requires two things: no new scenario for `plateau-after` (default 6h), and no scenario missing bodies it should have. `incomplete` names the ones that are short, so a non-zero `droppedEvents` or `writeErrors` tells you the corpus is thinner than the traffic was.

Route templates and counts only — no bodies — so it is as safe to expose as the rest of Actuator. Without Actuator the same numbers are in the trailing `STATISTICS` event of the corpus.

## Install

Java **17+**, Spring Boot **3.x**, Spring MVC (servlet). `traffictape-core` depends on Jackson and SLF4J only — nothing a Spring Boot application does not already have. Outbound support for `RestClient` (Boot 3.2+) and OkHttp activates only when those classes are on the classpath.

```xml
<dependency>
    <groupId>io.github.arun0009</groupId>
    <artifactId>traffictape-spring-boot</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Inject `RestClient.Builder` / `RestTemplateBuilder` / `WebClient.Builder` so outbound interceptors apply. `RestClient.create()` bypasses customization. An `OkHttpClient` (or `OkHttpClient.Builder`) **bean** is wrapped automatically; `new OkHttpClient()` is not.

```yaml
traffictape:
  enabled: false   # turn on only in QA
  max-examples-per-scenario: 50
  output:
    directory: /tmp/traffic-tape
  capture:
    include:
      methods: [GET, POST, PUT, PATCH, DELETE]
    exclude:
      routes: [/health, /actuator/**]
      content-types: [multipart/form-data, application/octet-stream]
      request-headers:            # synthetic traffic on real endpoints
        X-Smoke-Test: ["*"]       # "*" = exclude on presence alone
        User-Agent: ["kube-probe/*"]
```

Excluding by route is not enough when smoke tests, probes, and load generators hit the *same* endpoints as real users. `exclude.request-headers` drops those by marker header, and drops the outbound calls they caused along with them — otherwise the corpus gains dependencies with no parent request. See [configuration](docs/configuration.md#excluding-synthetic-traffic).

Restart after changing `enabled` (v0.1 is startup-config only).

## Give it to Claude

Read `FOR_CLAUDE.md`, then `statistics.json` / `gaps.json` / `fanout.json`, then sampled events. CloudWatch: latest `STATISTICS` already includes `captureReady` and truncated gaps/fanout. Prompt: [AI workflow](docs/ai-workflow.md). Dump commands are under Fargate below.

## Extending (escape hatch)

We will not wrap every HTTP client. If yours is not RestClient / RestTemplate / WebClient / an `OkHttpClient` bean:

```java
captureEngine.record(ObservedExchange.builder()
        .direction(Direction.OUTBOUND)
        .method("GET")
        .path("/inventory/9")
        .status(200)
        .requestBody(req)
        .responseBody(res)
        .build());
```

A different sink, sampler, or fingerprint:

```java
@Bean
CaptureSink mySink() {
    return batch -> { /* your store */ };
}
```

Defaults back off via `@ConditionalOnMissingBean`.

## Corpus

```text
/tmp/traffic-tape/
  FOR_CLAUDE.md
  metadata.json
  statistics.json
  gaps.json
  fanout.json
  events/events-000001.jsonl.gz
```

Schema: [corpus format](docs/corpus-format.md).

## Generate mocks

```bash
java -jar traffictape-cli-0.1.0-all.jar generate --corpus /tmp/traffic-tape --out ./out
```

```text
Read 4182 events from 6 file(s): 37 inbound scenarios, 24 outbound scenarios.
Wrote 22 WireMock mapping(s) to out/wiremock/mappings
Wrote 4 Mountebank imposter(s) to out/mountebank/imposters.json
Wrote 37 test case(s) to out/test-plan.json
```

Outbound scenarios become stubs — those are the calls your service made. Inbound scenarios become `test-plan.json` cases, each naming the outbound stubs that the same request caused. Templated routes match by pattern, endpoints with several scenarios match on request *shape* so differing IDs do not break them, and scenarios are deduplicated by fingerprint so a four-task corpus does not produce four copies.

Details, including the collisions the tool cannot resolve for you: [generate](docs/generate.md).

## Modules

| Module | Role |
|---|---|
| `traffictape-core` | Engine, fingerprints, sampling, redaction, `CaptureSink` |
| `traffictape-sink-file` | Local gzip JSONL corpus |
| `traffictape-sink-s3` | S3 corpus (when a bucket is allowed) |
| `traffictape-sink-cloudwatch` | CloudWatch Logs (Fargate when S3 is blocked) |
| `traffictape-spring-boot` | Spring MVC + RestClient + RestTemplate + WebClient + OkHttp |
| `traffictape-cli` | Offline `generate`: corpus to WireMock / Mountebank / test plan |
| `traffictape-example` | Demo app |
| `traffictape-benchmarks` | JMH plus request-thread overhead checks (`make bench`) |

Spring is the first **adapter** and the CLI is the first **consumer**; the product is the corpus between them. Future runtimes should emit the same events, and anything that reads the schema works with the CLI unchanged.

### Fargate

If you **cannot** put objects in S3, use CloudWatch. Task roles usually already have log permissions. One group, one stream per task. Same JSON as the file corpus, plus a `STATISTICS` line each flush.

```xml
<dependency>
    <groupId>io.github.arun0009</groupId>
    <artifactId>traffictape-sink-cloudwatch</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```yaml
traffictape:
  enabled: true
  output:
    cloudwatch:
      log-group: /traffictape/qa/payments-api
```

Task role: `logs:CreateLogGroup`, `logs:CreateLogStream`, `logs:PutLogEvents`. Pull for Claude:

```text
# index (latest snapshot)
filter eventType = "STATISTICS"
| sort @timestamp desc
| limit 1

# examples, group by endpoint
filter eventType = "HTTP_TRANSACTION"
| stats count() by fingerprints.endpoint.label
```

```text
aws logs filter-log-events --log-group-name /traffictape/qa/payments-api \
  --query 'events[*].message' --output text > events.jsonl
```

If security **does** allow a bucket, `traffictape-sink-s3` writes the file tree (`FOR_CLAUDE.md`, `metadata.json`, `statistics.json`, `gaps.json`, `fanout.json`, gzip JSONL) instead.

## v0.1 limits

- Inbound capture is **Spring MVC (servlet) only**. WebFlux inbound is not supported.
- **Async servlet** dispatches (`DeferredResult`, `Callable`, `AsyncContext`) lose inbound/outbound correlation — outbound calls made on the async thread are not linked to their parent.
- WebClient **request** bodies are not rematerialized (responses are).
- Plain-text bodies cannot be field-redacted; unparseable (usually truncated) JSON is omitted.
- Inbound responses are still teed after a scenario’s example budget is full (enqueueing stops).
- Sampler budget is per JVM (four tasks × 100 examples ≈ 400 events); `traffictape-cli` deduplicates by scenario fingerprint offline.
- The CLI emits mock definitions and a test plan as **data**. It does not generate Karate or JUnit source.
- Two scenarios that differ only by response status cannot be told apart by a request matcher; the CLI keeps the success case and reports the other.

## Docs

- [Architecture](docs/architecture.md)
- [Capture from your test suite](docs/capture-from-tests.md)
- [Corpus format](docs/corpus-format.md)
- [Generate mocks](docs/generate.md)
- [Configuration](docs/configuration.md)
- [Sampling](docs/sampling.md)
- [Redaction](docs/redaction.md)
- [AI workflow](docs/ai-workflow.md)

## Contributing

Issues and pull requests welcome at [github.com/arun0009/traffictape](https://github.com/arun0009/traffictape). See [CONTRIBUTING](CONTRIBUTING.md). Released under the [MIT License](LICENSE).
