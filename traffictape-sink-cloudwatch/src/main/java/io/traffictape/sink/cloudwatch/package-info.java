/**
 * CloudWatch Logs {@link io.traffictape.capture.CaptureSink} for shops that
 * cannot land a corpus in S3. One group, one stream per task. Same
 * {@code HTTP_TRANSACTION} JSON as the file corpus, plus a {@code STATISTICS}
 * event each flush (the index).
 *
 * <p>Enable with {@code traffictape.output.cloudwatch.log-group}. File output
 * backs off via {@code @ConditionalOnMissingBean}. If S3 is also configured,
 * S3 wins.
 */
package io.traffictape.sink.cloudwatch;
