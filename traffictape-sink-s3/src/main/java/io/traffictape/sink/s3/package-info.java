/**
 * S3 {@link io.traffictape.capture.CaptureSink}. Add this module on Fargate; file output
 * backs off when {@code traffictape.output.s3.bucket} is set.
 *
 * <p>Each task writes {@code s3://bucket/{prefix}/{date}/{instance}/} so replicas
 * never share an object. Sampling is still per JVM.
 */
package io.traffictape.sink.s3;
