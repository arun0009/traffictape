# Changelog

## Unreleased

- Failed sink writes retry three times; remaining failures increment `lostEvents`.
- A full capture queue refunds the sampler slot.
- `BoundedScenarioSampler` caps unique scenario keys.
- Invalid `traffictape.*` numeric/duration values fail at startup.
- CLI validates `schemaVersion`, defaults `--format` to `wiremock`, and writes `junit/TrafficTapeReplayTest.java`.
- Smaller capture defaults: 10 examples/scenario, 64 KiB bodies, queue 2000, 10k fingerprints.
- Outbound adapters share `OutboundObservation` / `BoundedPrefix`; destination identity strips default HTTP/HTTPS ports.
- Unparseable truncated JSON is still flagged `truncated: true` when omitted.
- Corpus companion file is `README.md` (was `FOR_CLAUDE.md`).
