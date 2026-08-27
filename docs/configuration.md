# Configuration

Requires Java 17+ and Spring Boot 3.x on Spring MVC. Prefix: `traffictape`. Disabled unless `enabled: true`.

Every property ships configuration metadata, so IntelliJ IDEA and VS Code complete names, show
descriptions and defaults, and suggest values for the list properties. The lists below **replace**
their defaults rather than adding to them — `redaction.json-fields` especially, where dropping the
defaults silently stops redacting passwords and card numbers.

```yaml
traffictape:
  enabled: false
  max-examples-per-scenario: 50
  plateau-after: 6h
  max-request-bytes: 1048576
  max-response-bytes: 1048576
  queue-size: 100000
  max-unique-fingerprints: 50000
  shutdown-drain: 5s
  flush:
    interval: 30s
    max-events: 1000
    max-bytes: 52428800
  output:
    directory: /tmp/traffic-tape
    compression: gzip
    rotate-after-events: 1000
    rotate-after-bytes: 52428800
    # Fargate without S3: add traffictape-sink-cloudwatch.
    # cloudwatch:
    #   log-group: /traffictape/qa/payments-api
    #   log-stream: ""              # default: hostname; one stream per task
    #   region: us-east-1
    # If a bucket is allowed instead:
    # s3:
    #   bucket: qa-traffic-tape
    #   prefix: payments-api
    #   unique-per-instance: true

  destinations:
    "inventory.internal:8080": inventory-service
  capture:
    text-bodies: true      # false = omit XML, form-urlencoded, and plain text bodies
    include:
      methods: [GET, POST, PUT, PATCH, DELETE]
      headers: []          # empty = all except denylist
      json-fields: []      # empty = all except denylist
    exclude:
      routes: [/health, /actuator/**]
      content-types: [multipart/form-data, application/octet-stream]
      destinations: []
      request-headers:        # drop synthetic traffic; "*" = on presence alone
        X-Smoke-Test: ["*"]
        User-Agent: ["kube-probe/*", "*synthetic-monitor*"]
  redaction:
    enabled: true          # false disables all redaction and logs a WARN
    headers: [Authorization, Cookie, Set-Cookie, Proxy-Authorization, X-Api-Key]
    json-fields: [password, token, accessToken, refreshToken, secret, clientSecret, ssn, creditCard]
```

Safe default: **omit** rather than capture. Tighten `include.headers` / `include.json-fields` in a 70-service estate if you want an allow-list.

`redaction.json-fields` is applied to JSON bodies, XML leaf elements, and form-urlencoded pairs. See [redaction](redaction.md) for what each format covers and what it does not.

`max-examples-per-scenario` stops bodies per scenario. `plateau-after` (default 6h) sets `captureReady` when no new unique scenario appears. Later new scenarios still get their own N.

## Excluding synthetic traffic

`capture.exclude.routes` drops a request by path. `capture.exclude.request-headers` drops it by marker header, which is what you want for traffic that hits the same endpoints as real users: functional tests that run on startup, uptime monitors, load generators, canary probes.

```yaml
traffictape:
  capture:
    exclude:
      request-headers:
        X-Smoke-Test: ["*"]                    # any value; matches on presence
        User-Agent: ["kube-probe/*", "*synthetic-monitor*"]
```

Header names are case-insensitive and values are globbed case-insensitively. A pattern of `*` — or an empty list — matches whenever the header is present. A header with several values is excluded if any one matches.

Three things worth knowing:

- **Exclusion covers the whole exchange.** The outbound calls a suppressed request makes are dropped too, even though they do not carry the marker header. Keeping them would put dependencies in the corpus with no parent request, which read as real fan-out. Suppression is carried on the request thread and through the Reactor context for WebClient, so it shares the [async servlet limitation](../README.md#v01-limits): a call made on a thread the filter does not reach is not suppressed.
- **Suppression is in-process; it does not travel over the wire.** If a suppressed outbound call reaches another service that is also capturing, that service sees an ordinary inbound request — the marker header is on the original request, not on the hop your service made. Either configure the same exclusion there, or have the caller forward the marker header downstream.
- **Excluded traffic costs nothing.** The decision is made before request or response wrapping, so a suppressed request is not buffered at all.

This is not the same setting as `capture.include.headers`, which chooses *which headers to store* on exchanges that are being recorded. `exclude.request-headers` chooses *which exchanges to record at all*.

## Knowing when capture is done

`traffictape-spring-boot` registers an Actuator endpoint at `/actuator/traffictape` when
`spring-boot-actuator` is on the classpath and the endpoint is exposed. It is read-only and
contains route templates and counters only, never bodies or header values.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: [health, traffictape]
```

| Field | Meaning |
| --- | --- |
| `ready` | `plateauReached` and nothing missing. The signal to turn capture off. |
| `plateauReached` | No new scenario for `plateau-after` (default 6h). False until the first request. |
| `scenariosMissingExamples` | Scenarios with fewer stored bodies than their observations allowed. |
| `incomplete` | Those scenarios, worst first, capped at 20. |
| `droppedEvents` / `writeErrors` | Queue overflow and sink failures — the corpus is thinner than the traffic. |

The two conditions are independent on purpose. A plateau alone can mean traffic simply stopped;
complete bodies alone say nothing about behaviour you have not seen yet.

Reaching `ready` is not a guarantee of coverage, only that *this* environment stopped producing
new behaviour. A nightly or month-end job that has not run yet is still unseen behaviour.

Without Actuator the same numbers are in the trailing `STATISTICS` event of the corpus, which is
what `gaps.json` and `statistics.json` are built from.

## Flushing and file rotation

`flush.*` controls how the background writer batches; `output.rotate-after-*` controls when a new events file starts. They are deliberately separate, so flushing eagerly in a short-lived run does not produce one file per request.

A sink resumes numbering after the events files already in the directory and creates each file exclusively. Restarts, several Spring contexts in one test JVM, and parallel Surefire forks therefore append instead of overwriting each other.

`destinations` maps outbound host[:port] to a service name stored on the event.

## Fargate / CloudWatch

When S3 is blocked, add `traffictape-sink-cloudwatch` and set `traffictape.output.cloudwatch.log-group`. One group, one stream per task (hostname by default). The worker batches; this sink splits `PutLogEvents` under 1&nbsp;MB. Failed puts drop the batch.

Each flush writes:

- `HTTP_TRANSACTION` — same JSON as a corpus line
- `STATISTICS` — index plus `captureReady`, `lastNewScenarioAt`, truncated `gaps` and `fanout` (no extra fetch)

```yaml
traffictape:
  enabled: true
  output:
    cloudwatch:
      log-group: /traffictape/qa/payments-api
```

Task role: `logs:CreateLogGroup`, `logs:CreateLogStream`, `logs:PutLogEvents`. Insights:

```text
filter eventType = "STATISTICS" | sort @timestamp desc | limit 1
filter eventType = "HTTP_TRANSACTION" | stats count() by fingerprints.endpoint.label
```

Dump: `aws logs filter-log-events --log-group-name /traffictape/qa/payments-api --query 'events[*].message' --output text > events.jsonl`

## Fargate / S3

If a bucket is allowed, add `traffictape-sink-s3` instead. Task role needs `s3:PutObject`. Do not point four tasks at one key: default `unique-per-instance` writes:

```text
s3://qa-traffic-tape/payments-api/2026-08-27/{hostname}/
  FOR_CLAUDE.md
  metadata.json
  statistics.json
  gaps.json
  fanout.json
  events/events-000001.jsonl.gz
```

If both `s3.bucket` and `cloudwatch.log-group` are set, S3 wins. Sampling is still per JVM (`max-examples-per-scenario` × task count).
