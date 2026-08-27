# traffictape-sink-cloudwatch

Sends captured exchanges to CloudWatch Logs instead of local disk, batching `PutLogEvents` under 1 MB. For Fargate / ECS tasks where S3 is blocked.

Includes `traffictape-spring-boot`, so this single dependency is all you add.

```xml
<dependency>
    <groupId>io.github.arun0009</groupId>
    <artifactId>traffictape-sink-cloudwatch</artifactId>
    <version>${traffictape.version}</version>
</dependency>
```

```yaml
traffictape:
  enabled: true
  output:
    cloudwatch:
      log-group: /traffictape/qa/payments-api
```

Takes over only when `cloudwatch.log-group` is set; otherwise capture falls back to local files. One group, one stream per task (hostname by default). Task role needs `logs:CreateLogGroup`, `logs:CreateLogStream`, and `logs:PutLogEvents`.

This is a transport, not the canonical corpus: dump the log group back to JSONL before generating stubs.

[Configuration](../docs/configuration.md#fargate--cloudwatch)
