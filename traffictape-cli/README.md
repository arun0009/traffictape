# traffictape-cli

Turns a captured corpus into WireMock stubs, Mountebank imposters, and a `test-plan.json`. Offline: it opens no sockets and needs nothing from your application.

Not a build dependency. Download the self-contained `-all` jar from the [latest release](https://github.com/arun0009/traffictape/releases/latest).

```bash
java -jar traffictape-cli-${traffictape.version}-all.jar generate \
  --corpus /tmp/traffic-tape --out ./traffictape-out
```

Reads a corpus directory, its `events/` directory, or a single `.jsonl`/`.jsonl.gz` file, so a CloudWatch dump works as input.

[Generate](../docs/generate.md) · [AI workflow](../docs/ai-workflow.md)
