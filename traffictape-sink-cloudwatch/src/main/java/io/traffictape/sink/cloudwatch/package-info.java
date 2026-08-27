/**
 * CloudWatch Logs {@link io.traffictape.capture.CaptureSink} when you cannot land files in S3.
 * One group, one stream per task. Same {@code HTTP_TRANSACTION} JSON as the file layout,
 * plus a {@code STATISTICS} event each flush (the index).
 *
 * <p>Enable with {@code traffictape.output.cloudwatch.log-group}. File output is skipped
 * if a {@code CaptureSink} bean already exists. If S3 is also configured, S3 wins.
 */
package io.traffictape.sink.cloudwatch;
