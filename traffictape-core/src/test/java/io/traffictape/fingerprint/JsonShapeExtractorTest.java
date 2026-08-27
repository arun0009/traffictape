package io.traffictape.fingerprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonShapeExtractorTest {

    private final JsonShapeExtractor extractor = new JsonShapeExtractor(new ObjectMapper());

    @Test
    void differentFieldSetsProduceDifferentShapes() {
        String status = extractor.extract("{\"status\":\"ACTIVE\"}".getBytes());
        String owner = extractor.extract("{\"owner\":\"team-a\"}".getBytes());
        assertThat(status).isEqualTo("{status:string}");
        assertThat(owner).isEqualTo("{owner:string}");
        assertThat(status).isNotEqualTo(owner);
    }

    @Test
    void valuesAreTypesNotLiterals() {
        String shape = extractor.extract("{\"sourceAccount\":\"123\",\"amount\":100,\"currency\":\"USD\"}".getBytes());
        assertThat(shape).isEqualTo("{amount:number,currency:string,sourceAccount:string}");
        assertThat(shape).doesNotContain("123");
    }

    @Test
    void emptyIsNone() {
        assertThat(extractor.extract(new byte[0])).isEqualTo(JsonShapeExtractor.NONE);
    }
}
