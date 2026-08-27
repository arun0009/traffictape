# traffictape-overhead-tests

Tests that enforce the central promise — capture never slows the request — plus the JMH benchmarks. Not published to Maven Central.

- `CaptureOverheadTest` — request-thread overhead stays within a ratio budget, with and without body capture.
- `SinkIsolationTest` — a deliberately slow sink does not slow requests, and a second test confirms that sink is actually reached so the first cannot pass vacuously.

These assert on timing, so they are the most likely place to see noise on a busy CI runner. Investigate a failure before assuming flakiness.

```bash
make bench                          # from the repository root
make bench BENCH_ARGS='-f 3 -prof gc'
```

[Architecture](../../docs/architecture.md)
