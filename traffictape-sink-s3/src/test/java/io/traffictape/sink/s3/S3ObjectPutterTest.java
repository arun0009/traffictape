package io.traffictape.sink.s3;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class S3ObjectPutterTest {

    @Mock
    S3Client client;

    @Test
    void putsUnderTaskPrefixNotASharedFile() {
        S3ObjectPutter putter = new S3ObjectPutter(client, "qa-tape", "payments-api/2026-08-27/task-a");
        putter.put("events/events-000001.jsonl.gz", new byte[] {1, 2, 3}, "application/gzip");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo("qa-tape");
        assertThat(captor.getValue().key())
                .isEqualTo("payments-api/2026-08-27/task-a/events/events-000001.jsonl.gz");
        assertThat(captor.getValue().contentType()).isEqualTo("application/gzip");
    }

    @Test
    void twoTasksDoNotShareKeys() {
        assertThat(S3ObjectPutter.join("svc/2026-08-27/task-a", "metadata.json"))
                .isNotEqualTo(S3ObjectPutter.join("svc/2026-08-27/task-b", "metadata.json"));
    }
}
