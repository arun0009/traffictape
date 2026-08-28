package io.traffictape.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class GenerateCommandTest {

    /**
     * One inbound order with two outbound calls, a duplicate inbound event from a second instance,
     * two ledger scenarios that differ only by request shape, an inventory 404 that differs from
     * the 200 only by response, and a STATISTICS line that is not a transaction.
     */
    private static final String EVENTS = """
            {"schemaVersion":"1","eventType":"HTTP_TRANSACTION","direction":"INBOUND","timestamp":"2026-08-26T22:00:00Z","correlation":{"exchangeId":"ex-1","outboundCount":2},"method":"POST","route":"/orders","path":"/orders","query":{},"fingerprints":{"endpoint":{"id":"e-orders","label":"INBOUND POST /orders"},"scenario":{"id":"s-orders","label":"INBOUND POST /orders shape={sku:string} resp=201"}},"requestShape":"{sku:string}","responseCharacteristic":"201","latencyMs":42,"request":{"headers":{"Content-Type":["application/json"]},"contentType":"application/json","body":{"encoding":"JSON","body":{"sku":"abc"},"truncated":false,"sizeBytes":14,"capturedBytes":14}},"response":{"status":201,"headers":{"Content-Type":["application/json"],"Transfer-Encoding":["chunked"]},"contentType":"application/json","body":{"encoding":"JSON","body":{"id":"9"},"truncated":false,"sizeBytes":10,"capturedBytes":10}}}
            {"schemaVersion":"1","eventType":"HTTP_TRANSACTION","direction":"OUTBOUND","timestamp":"2026-08-26T22:00:00.010Z","correlation":{"parentExchangeId":"ex-1","sequence":1},"destination":"inventory.internal:8080","method":"GET","route":"/inventory/{sku}","path":"/inventory/abc","query":{"detail":["full"]},"fingerprints":{"endpoint":{"id":"e-inv","label":"OUTBOUND GET /inventory/{sku}"},"scenario":{"id":"s-inv-200","label":"OUTBOUND GET /inventory/{sku} resp=200"}},"requestShape":"none","responseCharacteristic":"200","latencyMs":5,"request":{"headers":{"Authorization":["[REDACTED]"]},"contentType":null,"body":{"encoding":"EMPTY","body":null,"truncated":false,"sizeBytes":0,"capturedBytes":0}},"response":{"status":200,"headers":{"Content-Type":["application/json"]},"contentType":"application/json","body":{"encoding":"JSON","body":{"sku":"abc","onHand":4},"truncated":false,"sizeBytes":24,"capturedBytes":24}}}
            {"schemaVersion":"1","eventType":"HTTP_TRANSACTION","direction":"OUTBOUND","timestamp":"2026-08-26T22:00:00.020Z","correlation":{"parentExchangeId":"ex-1","sequence":2},"destination":"ledger.internal:9090","method":"POST","route":"/ledger","path":"/ledger","query":{},"fingerprints":{"endpoint":{"id":"e-ledger","label":"OUTBOUND POST /ledger"},"scenario":{"id":"s-ledger-charge","label":"OUTBOUND POST /ledger shape={amount:number} resp=200"}},"requestShape":"{amount:number}","responseCharacteristic":"200","latencyMs":7,"request":{"headers":{},"contentType":"application/json","body":{"encoding":"JSON","body":{"amount":10},"truncated":false,"sizeBytes":13,"capturedBytes":13}},"response":{"status":200,"headers":{"Content-Type":["application/json"]},"contentType":"application/json","body":{"encoding":"JSON","body":{"posted":true},"truncated":false,"sizeBytes":15,"capturedBytes":15}}}
            {"schemaVersion":"1","eventType":"HTTP_TRANSACTION","direction":"INBOUND","timestamp":"2026-08-26T22:05:00Z","correlation":{"exchangeId":"ex-2","outboundCount":0},"method":"POST","route":"/orders","path":"/orders","query":{},"fingerprints":{"endpoint":{"id":"e-orders","label":"INBOUND POST /orders"},"scenario":{"id":"s-orders","label":"INBOUND POST /orders shape={sku:string} resp=201"}},"requestShape":"{sku:string}","responseCharacteristic":"201","latencyMs":40,"request":{"headers":{},"contentType":"application/json","body":{"encoding":"JSON","body":{"sku":"zzz"},"truncated":false,"sizeBytes":14,"capturedBytes":14}},"response":{"status":201,"headers":{},"contentType":"application/json","body":{"encoding":"JSON","body":{"id":"10"},"truncated":false,"sizeBytes":11,"capturedBytes":11}}}
            {"schemaVersion":"1","eventType":"HTTP_TRANSACTION","direction":"OUTBOUND","timestamp":"2026-08-26T22:06:00Z","correlation":{"parentExchangeId":"ex-3","sequence":1},"destination":"ledger.internal:9090","method":"POST","route":"/ledger","path":"/ledger","query":{},"fingerprints":{"endpoint":{"id":"e-ledger","label":"OUTBOUND POST /ledger"},"scenario":{"id":"s-ledger-refund","label":"OUTBOUND POST /ledger shape={refund:number} resp=200"}},"requestShape":"{refund:number}","responseCharacteristic":"200","latencyMs":8,"request":{"headers":{},"contentType":"application/json","body":{"encoding":"JSON","body":{"refund":3},"truncated":false,"sizeBytes":13,"capturedBytes":13}},"response":{"status":200,"headers":{},"contentType":"application/json","body":{"encoding":"JSON","body":{"reversed":true},"truncated":false,"sizeBytes":17,"capturedBytes":17}}}
            {"schemaVersion":"1","eventType":"HTTP_TRANSACTION","direction":"OUTBOUND","timestamp":"2026-08-26T22:07:00Z","correlation":{"parentExchangeId":"ex-4","sequence":1},"destination":"inventory.internal:8080","method":"GET","route":"/inventory/{sku}","path":"/inventory/nope","query":{"detail":["full"]},"fingerprints":{"endpoint":{"id":"e-inv","label":"OUTBOUND GET /inventory/{sku}"},"scenario":{"id":"s-inv-404","label":"OUTBOUND GET /inventory/{sku} resp=404"}},"requestShape":"none","responseCharacteristic":"404","latencyMs":3,"request":{"headers":{},"contentType":null,"body":{"encoding":"EMPTY","body":null,"truncated":false,"sizeBytes":0,"capturedBytes":0}},"response":{"status":404,"headers":{},"contentType":null,"body":{"encoding":"JSON","body":{"error":"missing"},"truncated":false,"sizeBytes":20,"capturedBytes":20}}}
            {"schemaVersion":"1","eventType":"HTTP_TRANSACTION","direction":"OUTBOUND","timestamp":"2026-08-26T22:00:00.030Z","correlation":{"parentExchangeId":"ex-1","sequence":3},"destination":"ledger.internal:9090","method":"POST","route":"/audit","path":"/audit","query":{},"fingerprints":{"endpoint":{"id":"e-audit","label":"OUTBOUND POST /audit"},"scenario":{"id":"s-audit","label":"OUTBOUND POST /audit resp=202"}},"requestShape":"{event:string}","responseCharacteristic":"202","latencyMs":2,"request":{"headers":{},"contentType":"application/json","body":{"encoding":"JSON","body":{"event":"created"},"truncated":false,"sizeBytes":20,"capturedBytes":20}},"response":{"status":202,"headers":{},"contentType":null,"body":{"encoding":"OMITTED","body":null,"truncated":false,"sizeBytes":0,"capturedBytes":0}}}
            {"eventType":"STATISTICS","captureReady":false}
            """;

    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path temp;

    private Path tapeDir;
    private Path out;
    private ByteArrayOutputStream stdout;
    private PrintStream printStream;

    @BeforeEach
    void setUp() throws Exception {
        tapeDir = temp.resolve("tape");
        Files.createDirectories(tapeDir.resolve("events"));
        Files.writeString(tapeDir.resolve("events").resolve("events-000001.jsonl"), EVENTS);
        out = temp.resolve("out");
        stdout = new ByteArrayOutputStream();
        printStream = new PrintStream(stdout, true, StandardCharsets.UTF_8);
    }

    private int generate(String... extra) {
        String[] base = {"generate", "--tape", tapeDir.toString(), "--out", out.toString()};
        String[] args = Stream.concat(Stream.of(base), Stream.of(extra)).toArray(String[]::new);
        return TrafficTapeCli.run(args, printStream, printStream);
    }

    private String output() {
        return stdout.toString(StandardCharsets.UTF_8);
    }

    private List<Path> mappingFiles() throws Exception {
        try (Stream<Path> files = Files.list(out.resolve("wiremock").resolve("mappings"))) {
            return files.sorted().toList();
        }
    }

    @Test
    void deduplicatesScenariosAcrossInstances() {
        assertThat(generate()).isZero();
        assertThat(output()).contains("1 inbound scenarios", "5 outbound scenarios");
    }

    @Test
    void skipsNonTransactionLinesWithoutCountingThemAsFailures() {
        assertThat(generate()).isZero();
        assertThat(output()).contains("Read 7 events");
        assertThat(output()).doesNotContain("could not be parsed");
    }

    @Test
    void writesOneMappingPerOutboundScenarioThatCanBeMatched() throws Exception {
        assertThat(generate("--format", "wiremock")).isZero();
        // The inventory 404 collides with the 200, so five scenarios yield four mappings.
        assertThat(mappingFiles()).hasSize(4);
        assertThat(output()).contains("Wrote 4 WireMock mapping(s)");
    }

    @Test
    void givesCollidingScenariosDistinctFileNames() throws Exception {
        assertThat(generate("--format", "wiremock")).isZero();
        List<String> names = mappingFiles().stream().map(p -> p.getFileName().toString()).toList();
        assertThat(names).doesNotHaveDuplicates();
        assertThat(names).anyMatch(n -> n.contains("s-ledger-charge"));
        assertThat(names).anyMatch(n -> n.contains("s-ledger-refund"));
    }

    @Test
    void reportsMatcherCollisionAndKeepsTheSuccessCase() throws Exception {
        assertThat(generate("--format", "wiremock")).isZero();
        assertThat(output()).contains("resp=404", "same request as", "resp=200");
        String all = readAll(mappingFiles());
        assertThat(all).contains("\"status\" : 200");
        assertThat(all).doesNotContain("\"status\" : 404");
    }

    @Test
    void usesRegexMatchingForTemplatedRoutesAndDropsRedactedHeaders() throws Exception {
        assertThat(generate("--format", "wiremock")).isZero();
        String all = readAll(mappingFiles());
        assertThat(all).contains("\"urlPathPattern\" : \"/inventory/[^/]+\"");
        assertThat(all).contains("\"urlPath\" : \"/ledger\"");
        assertThat(all).doesNotContain("REDACTED");
        assertThat(all).doesNotContain("Transfer-Encoding");
    }

    @Test
    void disambiguatesSameEndpointByRequestShape() throws Exception {
        assertThat(generate("--format", "wiremock")).isZero();
        String all = readAll(mappingFiles());
        assertThat(all).contains("$.['amount']");
        assertThat(all).contains("$.['refund']");
        assertThat(all).contains("\"priority\" : 1");
    }

    @Test
    void stubsOnlyOutboundCalls() throws Exception {
        assertThat(generate("--format", "wiremock")).isZero();
        // Inbound requests belong in the test plan, not in the mocks.
        assertThat(mappingFiles()).noneMatch(p -> p.getFileName().toString().contains("orders"));
    }

    @Test
    void singleScenarioEndpointGetsNoBodyMatching() throws Exception {
        assertThat(generate("--format", "wiremock")).isZero();
        Path audit = mappingFiles().stream()
                .filter(p -> p.getFileName().toString().contains("audit"))
                .findFirst().orElseThrow();
        String content = Files.readString(audit);
        assertThat(content).doesNotContain("bodyPatterns");
        assertThat(content).contains("\"priority\" : 5");
    }

    @Test
    void flagsOmittedResponseBodies() {
        assertThat(generate("--format", "wiremock")).isZero();
        assertThat(output()).contains("need attention", "omitted at capture time");
        assertThat(output()).contains("OUTBOUND POST /audit resp=202");
    }

    @Test
    void reportsOmittedBodiesForMountebankToo() {
        assertThat(generate("--format", "mountebank")).isZero();
        assertThat(output()).contains("omitted at capture time");
    }

    @Test
    void writesMountebankImpostersGroupedByDestination() throws Exception {
        assertThat(generate("--format", "mountebank", "--base-port", "5000")).isZero();
        JsonNode imposters = mapper.readTree(
                Files.readString(out.resolve("mountebank").resolve("imposters.json"))).get("imposters");
        assertThat(imposters).hasSize(2);
        assertThat(imposters.get(0).get("port").asInt()).isEqualTo(5000);
        assertThat(imposters.get(1).get("port").asInt()).isEqualTo(5001);
        // inventory contributes one stub after the 404 collision; ledger keeps three.
        assertThat(imposters.get(0).get("stubs")).hasSize(1);
        assertThat(imposters.get(1).get("stubs")).hasSize(3);

        JsonNode ports = mapper.readTree(
                Files.readString(out.resolve("mountebank").resolve("ports.json")));
        assertThat(ports.get("inventory.internal:8080").asInt()).isEqualTo(5000);
        assertThat(ports.get("ledger.internal:9090").asInt()).isEqualTo(5001);
    }

    @Test
    void ordersMountebankStubsMostSpecificFirst() throws Exception {
        assertThat(generate("--format", "mountebank")).isZero();
        JsonNode imposters = mapper.readTree(
                Files.readString(out.resolve("mountebank").resolve("imposters.json"))).get("imposters");
        JsonNode ledger = imposters.get(1);
        assertThat(ledger.get("name").asText()).isEqualTo("ledger.internal:9090");
        assertThat(ledger.get("stubs").get(0).toString()).contains("exists");
    }

    @Test
    void anchorsMountebankPathRegex() throws Exception {
        assertThat(generate("--format", "mountebank")).isZero();
        String content = Files.readString(out.resolve("mountebank").resolve("imposters.json"));
        assertThat(content).contains("^/inventory/[^/]+$");
    }

    @Test
    void linksInboundCasesToTheOutboundStubsTheyNeed() throws Exception {
        assertThat(generate()).isZero();
        JsonNode plan = mapper.readTree(Files.readString(out.resolve("test-plan.json")));
        assertThat(plan.get("tape").get("inboundScenarios").asInt()).isEqualTo(1);
        JsonNode only = plan.get("cases").get(0);
        assertThat(only.get("scenario").asText()).isEqualTo("s-orders");
        assertThat(only.get("expect").get("status").asInt()).isEqualTo(201);
        assertThat(only.get("request").get("body").get("sku").asText()).isEqualTo("abc");

        List<String> dependencies = new ArrayList<>();
        only.get("dependsOn").forEach(node -> dependencies.add(node.get("scenario").asText()));
        // Ordered by outbound sequence, not by discovery order.
        assertThat(dependencies).containsExactly("s-inv-200", "s-ledger-charge", "s-audit");
    }

    @Test
    void readsSingleFileDumps() throws Exception {
        Path dump = temp.resolve("dump.jsonl");
        Files.writeString(dump, EVENTS);
        int status = TrafficTapeCli.run(
                new String[]{"generate", "--tape", dump.toString(), "--out", out.toString()},
                printStream, printStream);
        assertThat(status).isZero();
        assertThat(output()).contains("Read 7 events from 1 file(s)");
    }

    @Test
    void readsGzippedTape() throws Exception {
        Path events = temp.resolve("gz").resolve("events");
        Files.createDirectories(events);
        try (java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(
                Files.newOutputStream(events.resolve("events-000001.jsonl.gz")))) {
            gz.write(EVENTS.getBytes(StandardCharsets.UTF_8));
        }
        int status = TrafficTapeCli.run(
                new String[]{"generate", "--tape", temp.resolve("gz").toString(),
                        "--out", out.toString()},
                printStream, printStream);
        assertThat(status).isZero();
        assertThat(output()).contains("Read 7 events");
    }

    @Test
    void rejectsUnknownFormatAndMissingTape() {
        assertThat(TrafficTapeCli.run(new String[]{"generate"}, printStream, printStream)).isEqualTo(2);
        assertThat(output()).contains("--tape is required");
        assertThat(generate("--format", "hoverfly")).isEqualTo(2);
        assertThat(output()).contains("--format must be one of");
    }

    @Test
    void stillAcceptsTheOldCorpusFlag() {
        int status = TrafficTapeCli.run(
                new String[]{"generate", "--corpus", tapeDir.toString(), "--out", out.toString()},
                printStream, printStream);
        assertThat(status).isZero();
        assertThat(output()).contains("Read 7 events");
    }

    @Test
    void failsWhenTapeHasNoTransactions() throws Exception {
        Path empty = temp.resolve("empty");
        Files.createDirectories(empty);
        Files.writeString(empty.resolve("only-stats.jsonl"), "{\"eventType\":\"STATISTICS\"}\n");
        int status = TrafficTapeCli.run(
                new String[]{"generate", "--tape", empty.toString(), "--out", out.toString()},
                printStream, printStream);
        assertThat(status).isEqualTo(1);
        assertThat(output()).contains("No HTTP_TRANSACTION events found");
    }

    @Test
    void defaultFormatWritesWireMockAndJunitButNotMountebank() {
        assertThat(generate()).isZero();
        assertThat(out.resolve("wiremock").resolve("mappings")).isDirectory();
        assertThat(out.resolve("junit").resolve("TrafficTapeReplayTest.java")).exists();
        assertThat(out.resolve("mountebank")).doesNotExist();
    }

    @Test
    void skipsUnsupportedSchemaVersions() throws Exception {
        Files.writeString(tapeDir.resolve("events").resolve("events-000002.jsonl"), """
                {"schemaVersion":"2","eventType":"HTTP_TRANSACTION","method":"GET","route":"/x","path":"/x"}
                """);
        assertThat(generate("--format", "wiremock")).isZero();
        assertThat(output()).contains("unsupported schemaVersion");
    }

    @Test
    void generatesFromPublishedExampleTape() {
        Path example = Path.of("..", "examples", "tape");
        assertThat(example).isDirectory();
        int status = TrafficTapeCli.run(
                new String[]{"generate", "--tape", example.toString(), "--out", out.toString()},
                printStream, printStream);
        assertThat(status).isZero();
        assertThat(out.resolve("wiremock").resolve("mappings")).isDirectory();
        assertThat(out.resolve("test-plan.json")).exists();
    }

    private static String readAll(List<Path> files) throws Exception {
        StringBuilder all = new StringBuilder();
        for (Path file : files) {
            all.append(Files.readString(file));
        }
        return all.toString();
    }
}
