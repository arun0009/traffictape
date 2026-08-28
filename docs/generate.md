# Generating mocks and a test plan

`traffictape-cli` reads a tape and writes mock definitions. It is offline: it opens no sockets and needs no application on the classpath.

Download the `-all` jar from the [latest release](https://github.com/arun0009/traffictape/releases/latest). It is self-contained; nothing to add to your build.

```bash
java -jar traffictape-cli-${traffictape.version}-all.jar generate \
  --tape /tmp/traffic-tape \
  --out ./traffictape-out
```

```text
--tape <path>     Tape directory, its events/ directory, or a single
                  .jsonl/.jsonl.gz file.
--out <dir>       Output directory. Default: ./traffictape-out
--format <f>      wiremock | mountebank | both. Default: wiremock
--base-port <n>   First port for Mountebank imposters. Default: 4545
```

## Output

```text
traffictape-out/
  wiremock/mappings/*.json    one stub per outbound scenario
  mountebank/                 only with --format mountebank|both
  test-plan.json              one case per inbound scenario
  junit/TrafficTapeReplayTest.java
```

Run them:

```bash
java -jar wiremock-standalone.jar --root-dir traffictape-out/wiremock
mb start --configfile traffictape-out/mountebank/imposters.json
```

## What gets stubbed

**Outbound** scenarios become mocks — those are the calls your application made, and the ones a hermetic test has to intercept. **Inbound** scenarios become test-plan cases, because those are the requests you replay against your own service.

Each case lists the outbound scenarios that the same inbound request caused, ordered by `sequence`:

```json
{
  "scenario": "s-orders",
  "label": "INBOUND POST /orders shape={sku:string} resp=201",
  "request": { "method": "POST", "route": "/orders", "body": { "sku": "abc" } },
  "expect": { "status": 201, "body": { "id": "9" } },
  "dependsOn": [
    { "scenario": "s-inv-200", "destination": "inventory.internal:8080" },
    { "scenario": "s-ledger-charge", "destination": "ledger.internal:9090" }
  ]
}
```

`test-plan.json` is data. `junit/TrafficTapeReplayTest.java` is a JUnit 5 parameterized skeleton that replays those cases against `TRAFFICTAPE_BASE_URL`. Start WireMock from `wiremock/` first.

## Deduplication

Scenarios are keyed by fingerprint, so a tape from several instances yields one stub per distinct behaviour rather than one copy per JVM. This is the offline half of the per-process sampler budget.

## Matching

| Tape | Stub |
|---|---|
| `route` with `{placeholders}` | `urlPathPattern` / Mountebank `matches`, one path segment per placeholder |
| `route` without placeholders | `urlPath` / Mountebank `equals` |
| Query parameter names | presence matchers (names are part of endpoint identity; values are not) |
| Several scenarios on one endpoint | request-body **shape** matchers on top-level fields, so differing IDs do not break matching |
| One scenario on an endpoint | no body matching, to avoid brittle stubs |

Shape-matched stubs get WireMock `priority: 1` and are ordered first for Mountebank, so they win over the catch-all for the same endpoint.

Response headers that redaction replaced are dropped rather than served as if they were real credentials, along with hop-by-hop headers (`Transfer-Encoding`, `Content-Length`, `Connection`, `Date`, and friends) that a mock server sets itself.

## What needs a human

The command prints an attention list and exits 0. Two cases matter:

- **Matcher collisions.** Two scenarios that differ only by response — a `200` and a `404` on the same request — cannot be told apart by any request matcher. The success case is written and the other is named, so you can wire it by hand with a WireMock scenario or a Mountebank response sequence.
- **Missing bodies.** A response body that was omitted or truncated at capture time cannot be replayed faithfully. The stub is still written, and the reason is recorded under `metadata.traffictape.warnings`.
