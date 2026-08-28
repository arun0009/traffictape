package io.traffictape.spring.outbound;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedPrefixTeeTest {

    @Test
    void passesEveryByteThroughAndKeepsAPrefix() throws Exception {
        ByteArrayOutputStream downstream = new ByteArrayOutputStream();
        BoundedPrefix.Tee tee = BoundedPrefix.tee(downstream, 4);
        tee.write("abcdefgh".getBytes(StandardCharsets.UTF_8));
        tee.flush();

        assertThat(downstream.toString(StandardCharsets.UTF_8)).isEqualTo("abcdefgh");
        assertThat(new String(tee.captured(), StandardCharsets.UTF_8)).isEqualTo("abcd");
        assertThat(tee.truncated()).isTrue();
        assertThat(tee.size()).isEqualTo(8);
    }
}
