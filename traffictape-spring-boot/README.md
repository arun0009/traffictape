# traffictape-spring-boot

The recorder. Auto-configures the inbound servlet filter, the outbound RestClient / RestTemplate / WebClient / OkHttp interceptors, the async capture worker, and the `/actuator/traffictape` endpoint.

This is the dependency to add for a local corpus on disk. The gzip JSONL file writer is built in.

```xml
<dependency>
    <groupId>io.github.arun0009</groupId>
    <artifactId>traffictape-spring-boot</artifactId>
    <version>${traffictape.version}</version>
</dependency>
```

```yaml
traffictape:
  enabled: true
  output:
    directory: /tmp/traffic-tape
```

Off unless `enabled: true`. Writing to S3 or CloudWatch instead? Use `traffictape-sink-s3` or `traffictape-sink-cloudwatch`, which include this module — do not add both.

[Configuration](../docs/configuration.md) · [Capture from tests](../docs/capture-from-tests.md)
