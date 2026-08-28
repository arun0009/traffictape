# Configuration

Requires Java 17+ and Spring Boot 3.x on Spring MVC or JAX-RS/Jersey (servlet). Prefix: `traffictape`. Disabled unless `enabled: true`.

Every property ships configuration metadata, so IntelliJ IDEA and VS Code complete names, show
descriptions and defaults, and suggest values for the list properties. The lists below **replace**
their defaults rather than adding to them — `redaction.json-fields` especially, where dropping the
defaults silently stops redacting passwords and card numbers.

```yaml
traffictape:
  enabled: false
  max-examples-per-scenario: 10
  plateau-after: 6h
  max-request-bytes: 65536
  max-response-bytes: 65536
  queue-size: 2000
  max-unique-fingerprints: 10000
  shutdown-drain: 5s
  flush:
    interval: 30s
    max-events: 1000
    max-bytes: 52428800
  output:
    directory: /tmp/traffic-tape
    rotate-after-events: 1000
    rotate-after-bytes: 52428800
    # console: true            # JSON lines on logger traffictape.tape instead of files

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
    headers: [Authorization, Cookie, Set-Cookie, Proxy-Authorization, X-Api-Key, Api-Key]
    json-fields: [password, token, accessToken, refreshToken, secret, clientSecret, ssn, creditCard, cardNumber, cvv]
```

Safe default: **omit** rather than capture. Tighten `include.headers` / `include.json-fields` if you want an allow-list.

`redaction.json-fields` is applied to JSON fields at any depth, XML elements and attributes, and form-urlencoded pairs. See [redaction](redaction.md) for what each format covers and what it does not.

`max-examples-per-scenario` (default 10) stops bodies per scenario. The sampler keys on endpoint fingerprint + request shape + response status — not a 2xx quota. `plateau-after` (default 6h) sets `captureReady` when no new unique scenario appears. Later new scenarios still get their own N.

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

- **Exclusion covers the whole exchange.** The outbound calls a suppressed request makes are dropped too, even though they do not carry the marker header. Keeping them would put dependencies on the tape with no parent request, which read as real fan-out. Suppression is carried on the request thread and through the Reactor context for WebClient, so it shares the [async servlet limitation](../README.md#limits): a call made on a thread the filter does not reach is not suppressed.
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
| `droppedEvents` | Queue overflow. The event never reached the sink; the sampler slot is refunded. |
| `writeErrors` | Sink write attempts that failed (each batch is retried three times). |
| `lostEvents` | Events in batches that still failed after retries. The tape is thinner than the traffic. |
| `sinkDisabled` | The default file sink could not create its directory. The console logger never sets this; check `lostEvents` / `writeErrors` instead. |

The two conditions are independent on purpose. A plateau alone can mean traffic simply stopped;
complete bodies alone say nothing about behaviour you have not seen yet.

Reaching `ready` is not a guarantee of coverage, only that *this* environment stopped producing
new behaviour. A nightly or month-end job that has not run yet is still unseen behaviour.

Without Actuator the same numbers are in `statistics.json` and `gaps.json` on the tape.

## Flushing and file rotation

`flush.*` controls how the background writer batches; `output.rotate-after-*` controls when a new events file starts. They are deliberately separate, so flushing eagerly in a short-lived run does not produce one file per request.

A sink resumes numbering after the events files already in the directory and creates each file exclusively. Restarts, several Spring contexts in one test JVM, and parallel Surefire forks therefore append instead of overwriting each other.

`destinations` maps outbound host[:port] to a service name stored on the event.

## Where the tape goes

This library writes a tape. It does not create buckets, log groups, or IAM.

- **Files (default).** Gzip JSONL under `output.directory`. Copy the directory off the box (volume, `kubectl cp`, CI artifact).
- **JSON lines.** `output.console: true` writes one JSON object per event to logger `traffictape.tape` instead of files. Point your log driver at that logger; dump the lines to a `.jsonl` file before `generate`.
- **Anything else.** A `@Bean CaptureSink`. `ObjectStoreCaptureSink` writes the same tree through a put callback if you already have a store.

```yaml
traffictape:
  enabled: true
  output:
    console: true
```

A `@Bean CaptureSink` always wins over both of the above.
