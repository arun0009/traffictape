# traffictape-core

The capture engine, with no framework on it: HTTP transaction model, fingerprinting, scenario sampling, redaction, inbound/outbound correlation, the `CaptureSink` interface, and the default gzip JSONL file writer.

You do not add this yourself. Every other module brings it in.

Add it directly only if you are writing your own adapter for a framework other than Spring MVC, or your own `CaptureSink`.

```xml
<dependency>
    <groupId>io.github.arun0009</groupId>
    <artifactId>traffictape-core</artifactId>
    <version>${traffictape.version}</version>
</dependency>
```

[Architecture](../docs/architecture.md) · [File format](../docs/corpus-format.md)
