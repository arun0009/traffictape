# AI / offline workflow

TrafficTape does not generate Karate or WireMock. The corpus is the bridge.

## Inputs

1. `FOR_CLAUDE.md`
2. `statistics.json` / `gaps.json` / `fanout.json` (or latest CloudWatch `STATISTICS`)
3. Sampled `events/*.jsonl.gz` (or `HTTP_TRANSACTION` lines)
4. Existing Karate tests
5. Application source (optional)

## Prompt shape

```text
Analyze this real QA traffic corpus against the existing Karate tests.

Identify high-frequency and behaviorally significant request patterns
that are not adequately covered. Do not treat endpoint coverage as
scenario coverage: PATCH /assets/{id} with different request shapes
and statuses are different regression cases.

For each important gap:

1. Generate a Karate regression test.
2. Preserve meaningful assertions rather than snapshotting dynamic values.
3. Group outbound HTTP calls by parentExchangeId / sequence (see fanout.json).
4. Generate WireMock or Mountebank mocks for those outbound calls.
5. Use captured responses as realistic mock bodies.
6. Parameterize IDs, timestamps, tokens.
7. Do not duplicate identical scenario fingerprints.
8. Prefer representative scenarios over maximizing test count.
```

## CloudWatch dump

```text
filter eventType = "STATISTICS" | sort @timestamp desc | limit 1
filter eventType = "HTTP_TRANSACTION"
```

```text
aws logs filter-log-events --log-group-name /traffictape/qa/payments-api \
  --query 'events[*].message' --output text > events.jsonl
```
