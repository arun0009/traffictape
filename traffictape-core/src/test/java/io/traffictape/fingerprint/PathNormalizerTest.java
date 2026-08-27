package io.traffictape.fingerprint;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PathNormalizerTest {

    private final PathNormalizer normalizer = new PathNormalizer();

    @Test
    void numericIdsBecomePlaceholder() {
        assertThat(normalizer.normalize("/accounts/123")).isEqualTo("/accounts/{id}");
        assertThat(normalizer.normalize("/accounts/456/transactions/9"))
                .isEqualTo("/accounts/{id}/transactions/{id}");
    }

    @Test
    void uuidsBecomePlaceholder() {
        assertThat(normalizer.normalize("/assets/550e8400-e29b-41d4-a716-446655440000"))
                .isEqualTo("/assets/{uuid}");
    }

    @Test
    void prefersSpringTemplate() {
        assertThat(normalizer.preferTemplate("/accounts/{id}", "/accounts/99"))
                .isEqualTo("/accounts/{id}");
    }

    @Test
    void samePatternForDifferentIds() {
        assertThat(normalizer.normalize("/accounts/123"))
                .isEqualTo(normalizer.normalize("/accounts/456"));
    }
}
