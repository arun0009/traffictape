package io.traffictape.sink.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3 output. Each task writes under its own prefix so Fargate replicas do not
 * share a single object.
 */
@ConfigurationProperties(prefix = "traffictape.output.s3")
public class TrafficTapeS3Properties {

    /**
     * Bucket name. Empty = S3 sink stays off (file sink remains the default).
     */
    private String bucket = "";
    /**
     * Key prefix, typically the service name. With {@link #uniquePerInstance}
     * this becomes {@code {prefix}/{date}/{instance}/}.
     */
    private String prefix = "";
    /**
     * Optional. Fargate already sets {@code AWS_REGION}.
     */
    private String region = "";
    /**
     * When true (default), each JVM writes a unique prefix so four tasks cannot
     * overwrite the same keys. Sampling stays per process: 4 × 100 examples is 400
     * objects, not one shared file.
     */
    private boolean uniquePerInstance = true;

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public boolean isUniquePerInstance() {
        return uniquePerInstance;
    }

    public void setUniquePerInstance(boolean uniquePerInstance) {
        this.uniquePerInstance = uniquePerInstance;
    }
}
