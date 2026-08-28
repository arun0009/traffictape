# traffictape-spring-boot

The recorder. Auto-configures the inbound servlet filter (Spring MVC and JAX-RS/Jersey), outbound RestClient / RestTemplate / WebClient / OkHttp / JAX-RS `Client` interceptors, the async capture worker, and the `/actuator/traffictape` endpoint.

This is the dependency to add for a local corpus on disk; it writes gzip JSONL there by default.

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
