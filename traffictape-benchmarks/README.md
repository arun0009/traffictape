# traffictape-benchmarks

JMH benchmarks plus tests asserting that capture stays off the request thread. Not published to Maven Central.

The point is the guarantee, not the numbers: recording must not add latency to the request it observes.

```bash
make bench                          # from the repository root
make bench BENCH_ARGS='-f 3 -prof gc'
```

[Architecture](../docs/architecture.md)
