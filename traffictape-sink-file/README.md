# traffictape-sink-file

Writes the corpus to a local directory as gzipped JSONL, rotating files by event count or size. This is the default destination.

You do not add this yourself. `traffictape-spring-boot` brings it in and uses it unless another sink is present.

```yaml
traffictape:
  output:
    directory: /tmp/traffic-tape
    rotate-after-events: 1000
    rotate-after-bytes: 52428800
```

[Configuration](../docs/configuration.md) · [File format](../docs/corpus-format.md)
