# Changelog

## Unreleased

### Breaking

- `traffictape-sink-s3` and `traffictape-sink-cloudwatch` are removed. This library does not create buckets, log groups, or IAM. Default output is gzip JSONL files. Optional `traffictape.output.console=true` writes JSON lines to logger `traffictape.corpus` (your log driver ships them). A custom store is a `@Bean CaptureSink`; `ObjectStoreCaptureSink` writes the same tree through a put callback.

## 0.5.1 — 2026-08-28

- WebClient and JAX-RS client request bodies are teed as they are written (JSON POJOs included). SSE responses stay metadata-only.

## 0.5.0 — 2026-08-28

### Breaking

- CLI `--format` default is `wiremock` (was `both`). Pass `--format both` for WireMock and Mountebank.
- Corpus companion file is `README.md` (was `FOR_CLAUDE.md`).
- Capture defaults: 10 examples per scenario, 64 KiB bodies, queue 2000, 10k fingerprints.
- Destination identity omits default HTTP `:80` and HTTPS `:443`. Keys in `traffictape.destinations` must match that form.
- `CaptureMetrics.setEnabled` is removed. The Micrometer gauge is always registered when capture is on.

### Changed

- Failed sink writes retry three times; remaining failures increment `lostEvents`.
- A full capture queue refunds the sampler slot.
- `BoundedScenarioSampler` caps unique scenario keys.
- Invalid `traffictape.*` numeric/duration values fail at startup.
- CLI validates `schemaVersion` and writes `junit/TrafficTapeReplayTest.java`.
- Outbound adapters share `OutboundObservation` / `BoundedPrefix`.
- Unparseable truncated JSON is still flagged `truncated: true` when omitted.
- Actuator, `metadata.json`, and `statistics.json` include `lostEvents` / `writeErrors`.
