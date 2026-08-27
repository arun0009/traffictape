# traffictape-sink-s3

Writes the corpus to S3 instead of local disk, one prefix per task. For Fargate / ECS, where container disks do not outlive the task.

Includes `traffictape-spring-boot`, so this single dependency is all you add.

```xml
<dependency>
    <groupId>io.github.arun0009</groupId>
    <artifactId>traffictape-sink-s3</artifactId>
    <version>${traffictape.version}</version>
</dependency>
```

```yaml
traffictape:
  enabled: true
  output:
    s3:
      bucket: qa-traffic-tape
```

Takes over only when `s3.bucket` is set; otherwise capture falls back to local files. Task role needs `s3:PutObject`. If a CloudWatch log group is also configured, S3 wins.

[Configuration](../docs/configuration.md#fargate--s3)
