# Configuration

Java 17+, Spring Boot 3.x, Spring MVC or JAX-RS/Jersey on a servlet container. Prefix `traffictape`. Off unless `enabled: true`.

All defaults below. A list you set **replaces** the default list; setting `redaction.json-fields` without `password` stops redacting passwords.

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
    on-demand-header: X-TrafficTape-Record   # "" disables
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

Default is to omit, not capture. `include.headers` and `include.json-fields` turn the denylists into allow-lists.

`redaction.json-fields` applies to JSON at any depth, XML elements and attributes, and form-urlencoded pairs. Details: [redaction](redaction.md).

`max-examples-per-scenario` caps bodies per scenario, keyed on endpoint + request shape + response status. `plateau-after` sets `captureReady` once no new scenario has appeared for that long; a scenario that appears later still gets its own N.

## Synthetic traffic

`capture.exclude.request-headers` drops requests by marker header: startup smoke tests, uptime probes, load generators.

```yaml
traffictape:
  capture:
    exclude:
      request-headers:
        X-Smoke-Test: ["*"]                    # any value
        User-Agent: ["kube-probe/*", "*synthetic-monitor*"]
```

Names are case-insensitive, values are case-insensitive globs, `*` or an empty list matches on presence, and one matching value out of several is enough.

- The outbound calls of an excluded request are dropped with it, so the tape never shows fan-out with no parent. This rides the request thread and the Reactor context, so it has the same [async servlet limit](../README.md#limits) as capture.
- Exclusion is in-process. A downstream service that is also recording sees a normal inbound request unless the marker header is forwarded.
- Excluded requests are not wrapped or buffered.

`capture.include.headers` chooses which headers to store; `exclude.request-headers` chooses which requests to record.

## On demand

Once a scenario has its N examples, further requests to it are counted but not stored. A request carrying the on-demand header is stored anyway.

```bash
curl -H 'X-TrafficTape-Record: BUG-1234' https://qa.example.com/widgets/123
```

- Bypasses the sampler without using a slot. Route and content-type excludes, body caps, and redaction still apply.
- The outbound calls it makes are stored too, with the same tag.
- The value is written to `correlation.onDemandTag`; the header itself is not written. A header with no value gives `true`.
- `capture.on-demand-header: ""` turns it off. Rename it if the default could arrive from outside your network.

## Knowing when capture is done

With `spring-boot-actuator` on the classpath, `/actuator/traffictape` reports read-only counters and route templates. No bodies or header values.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: [health, traffictape]
```

| Field | Meaning |
| --- | --- |
| `ready` | `plateauReached` and nothing missing. Turn capture off. |
| `plateauReached` | No new scenario for `plateau-after`. False until the first request. |
| `scenariosMissingExamples` | Scenarios with fewer bodies than they could have had. |
| `incomplete` | Those scenarios, worst first, max 20. |
| `droppedEvents` | Queue overflow; slot refunded. |
| `writeErrors` | Failed sink writes (three retries per batch). |
| `lostEvents` | Events lost after retries. |
| `sinkDisabled` | File sink could not create its directory. Never set by the console logger. |

`ready` means this environment stopped producing new behaviour, not full coverage. Without Actuator, read `statistics.json` and `gaps.json`.

## Flushing and file rotation

`flush.*` batches the background writer; `output.rotate-after-*` starts a new events file. Files are numbered after the ones already present and created exclusively, so restarts, several Spring contexts in one JVM, and parallel Surefire forks append rather than overwrite.

`destinations` maps an outbound host[:port] to the service name written on the event.

## Where the tape goes

- **Files (default).** Gzip JSONL under `output.directory`. Copy the directory off the box.
- **JSON lines.** `output.console: true` logs one JSON object per event to logger `traffictape.tape`. Your log driver ships them; dump to a `.jsonl` file before `generate`.
- **Anything else.** A `@Bean CaptureSink`. `ObjectStoreCaptureSink` writes the same tree through a put callback.

```yaml
traffictape:
  enabled: true
  output:
    console: true
```

A `@Bean CaptureSink` always wins over both of the above.
