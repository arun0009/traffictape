# Configuration

Prefix: `traffictape`. Disabled unless `enabled: true`.

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
    include:
      methods: [GET, POST, PUT, PATCH, DELETE]
      headers: []          # empty = all except denylist
      json-fields: []      # empty = all except denylist
    exclude:
      routes: [/health, /actuator/**]
      content-types: [multipart/form-data, application/octet-stream]
      destinations: []
  redaction:
    enabled: true
    headers: [Authorization, Cookie, Set-Cookie, Proxy-Authorization, X-Api-Key]
    json-fields: [password, token, accessToken, refreshToken, secret, clientSecret, ssn, creditCard]
```

Safe default: **omit** rather than capture. Tighten `include.headers` / `include.json-fields` in a 70-service estate if you want an allow-list.

`max-examples-per-scenario` stops bodies per scenario. `plateau-after` (default 6h) sets `captureReady` when no new unique scenario appears. Later new scenarios still get their own N.

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
