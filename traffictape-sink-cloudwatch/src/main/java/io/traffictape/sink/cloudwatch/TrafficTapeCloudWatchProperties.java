package io.traffictape.sink.cloudwatch;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CloudWatch Logs transport. Empty {@code log-group} keeps this sink off
 * (file remains the default).
 */
@ConfigurationProperties(prefix = "traffictape.output.cloudwatch")
public class TrafficTapeCloudWatchProperties {

    /**
     * Log group, e.g. {@code /traffictape/qa/payments-api}. Empty = off.
     */
    private String logGroup = "";
    /**
     * Stream within the group. Empty = hostname (or a short UUID).
     * Four Fargate tasks should not share one stream.
     */
    private String logStream = "";
    /**
     * Optional. Fargate already sets {@code AWS_REGION}.
     */
    private String region = "";

    public String getLogGroup() {
        return logGroup;
    }

    public void setLogGroup(String logGroup) {
        this.logGroup = logGroup;
    }

    public String getLogStream() {
        return logStream;
    }

    public void setLogStream(String logStream) {
        this.logStream = logStream;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }
}
