package io.traffictape.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The IDE experience is a build artefact, not source, so nothing else would notice it breaking.
 *
 * <p>Defaults for collections cannot be inferred by the annotation processor and are hand-written
 * in {@code additional-spring-configuration-metadata.json}. That makes them free to drift from the
 * Java defaults, which is worse than absent: an autocomplete popup asserting that
 * {@code redaction.json-fields} defaults to a list it no longer defaults to is a security claim
 * the code does not honour.
 */
class ConfigurationMetadataTest {

    /** Produced by the annotation processor; only refreshed when Java sources recompile. */
    private static JsonNode generated;

    /**
     * The hand-written half. Read directly rather than through the merged output, which a
     * resources-only edit leaves stale — that staleness would let drift pass unnoticed.
     */
    private static JsonNode handWritten;

    @BeforeAll
    static void load() throws Exception {
        generated = read("/META-INF/spring-configuration-metadata.json");
        handWritten = read("/META-INF/additional-spring-configuration-metadata.json");
    }

    private static JsonNode read(String resource) throws Exception {
        try (InputStream in = ConfigurationMetadataTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("missing configuration metadata: %s", resource).isNotNull();
            return new ObjectMapper().readTree(in);
        }
    }

    @Test
    void everyPropertyIsDocumentedForAutocomplete() {
        List<String> undocumented = new ArrayList<>();
        properties(generated).forEach((name, node) -> {
            if (name.startsWith("traffictape.") && node.path("description").asText("").isBlank()) {
                undocumented.add(name);
            }
        });
        assertThat(undocumented)
                .as("a property with no Javadoc shows up bare in the IDE")
                .isEmpty();
    }

    @Test
    void handWrittenCollectionDefaultsMatchTheJavaDefaults() {
        TrafficTapeProperties actual = new TrafficTapeProperties();
        Map<String, Supplier<List<String>>> declared = new LinkedHashMap<>();
        declared.put("traffictape.capture.include.methods",
                () -> actual.getCapture().getInclude().getMethods());
        declared.put("traffictape.capture.exclude.routes",
                () -> actual.getCapture().getExclude().getRoutes());
        declared.put("traffictape.capture.exclude.content-types",
                () -> actual.getCapture().getExclude().getContentTypes());
        declared.put("traffictape.redaction.headers",
                () -> actual.getRedaction().getHeaders());
        declared.put("traffictape.redaction.json-fields",
                () -> actual.getRedaction().getJsonFields());

        Map<String, JsonNode> source = properties(handWritten);
        declared.forEach((name, supplier) -> {
            assertThat(source).as("%s needs a hand-written default", name).containsKey(name);
            JsonNode defaults = source.get(name).path("defaultValue");
            assertThat(defaults.isArray())
                    .as("%s has a non-empty Java default that the IDE cannot infer", name)
                    .isTrue();
            List<String> fromMetadata = new ArrayList<>();
            defaults.forEach(v -> fromMetadata.add(v.asText()));
            assertThat(fromMetadata)
                    .as("declared default for %s drifted from the code", name)
                    .isEqualTo(supplier.get());
        });
    }

    @Test
    void everyHintPointsAtAPropertyThatExists() {
        Set<String> known = new LinkedHashSet<>(properties(generated).keySet());
        List<String> dangling = new ArrayList<>();
        for (JsonNode hint : handWritten.path("hints")) {
            String name = hint.path("name").asText();
            // Map-valued properties are hinted as <property>.keys and <property>.values.
            String property = name.endsWith(".keys") || name.endsWith(".values")
                    ? name.substring(0, name.lastIndexOf('.'))
                    : name;
            if (!known.contains(property)) {
                dangling.add(name);
            }
        }
        assertThat(dangling)
                .as("a hint whose property name is wrong is silently ignored by the IDE")
                .isEmpty();
    }

    @Test
    void theSecuritySensitiveListsOfferValueSuggestions() {
        assertThat(hintNames())
                .contains("traffictape.redaction.headers", "traffictape.redaction.json-fields");
    }

    private static Set<String> hintNames() {
        Set<String> names = new LinkedHashSet<>();
        handWritten.path("hints").forEach(h -> names.add(h.path("name").asText()));
        return names;
    }

    private static Map<String, JsonNode> properties(JsonNode document) {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        document.path("properties").forEach(p -> out.put(p.path("name").asText(), p));
        return out;
    }
}
