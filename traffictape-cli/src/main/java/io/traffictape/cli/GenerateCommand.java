package io.traffictape.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reads recorded events, writes mock definitions and a test plan, and reports what needs attention. */
final class GenerateCommand {

    private final ObjectWriter writer = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .writerWithDefaultPrettyPrinter();

    int execute(TrafficTapeCli.Options options, PrintStream out) throws IOException {
        CorpusReader.Result read = new CorpusReader().read(options.corpus());
        if (read.transactions().isEmpty()) {
            out.println("No HTTP_TRANSACTION events found in " + options.corpus());
            return 1;
        }
        Corpus corpus = Corpus.index(read.transactions());

        out.printf("Read %d events from %d file(s): %d inbound scenarios, %d outbound scenarios.%n",
                read.transactions().size(), read.filesRead(),
                corpus.inboundScenarios().size(), corpus.outboundScenarios().size());
        if (read.filesTruncated() > 0) {
            out.printf("  %d file(s) ended mid-stream; earlier events in them were kept.%n",
                    read.filesTruncated());
        }
        if (read.linesFailed() > 0) {
            out.printf("  %d line(s) could not be parsed and were ignored.%n", read.linesFailed());
        }

        Files.createDirectories(options.out());
        StubPlan plan = StubPlan.of(corpus);
        List<String> attention = new ArrayList<>(plan.collisions());
        for (StubPlan.Stub stub : plan.stubs()) {
            for (String warning : StubSupport.bodyWarnings(stub.transaction())) {
                attention.add(stub.label() + ": " + warning);
            }
        }

        if (options.wiremock()) {
            List<WireMockGenerator.Mapping> mappingList = new WireMockGenerator().generate(plan);
            Path mappings = options.out().resolve("wiremock").resolve("mappings");
            Files.createDirectories(mappings);
            for (WireMockGenerator.Mapping mapping : mappingList) {
                write(mappings.resolve(mapping.fileName()), mapping.json());
            }
            out.printf("Wrote %d WireMock mapping(s) to %s%n", mappingList.size(), mappings);
            out.printf("  java -jar wiremock-standalone.jar --root-dir %s%n",
                    options.out().resolve("wiremock"));
        }

        if (options.mountebank()) {
            MountebankGenerator.Result result = new MountebankGenerator()
                    .generate(plan, options.basePort());
            Path directory = options.out().resolve("mountebank");
            Files.createDirectories(directory);
            Path imposters = directory.resolve("imposters.json");
            write(imposters, Map.of("imposters", result.imposters()));
            write(directory.resolve("ports.json"), result.ports());
            out.printf("Wrote %d Mountebank imposter(s) to %s%n", result.imposters().size(), imposters);
            out.printf("  mb start --configfile %s%n", imposters);
            if (!result.ports().isEmpty()) {
                out.println("  Point the application at these ports:");
                result.ports().forEach((destination, port) ->
                        out.printf("    %-40s -> localhost:%d%n", destination, port));
            }
        }

        Path testPlan = options.out().resolve("test-plan.json");
        write(testPlan, new TestPlanGenerator().generate(corpus));
        out.printf("Wrote %d test case(s) to %s%n", corpus.inboundScenarios().size(), testPlan);

        if (corpus.outboundScenarios().isEmpty()) {
            attention.add("No outbound calls were captured, so there is nothing to mock. "
                    + "Check that HTTP clients are built from the injected builders.");
        }
        if (!attention.isEmpty()) {
            out.println();
            out.printf("%d item(s) need attention:%n", attention.size());
            attention.forEach(item -> out.println("  - " + item));
        }
        return 0;
    }

    private void write(Path target, Object value) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(target, writer.writeValueAsString(value) + System.lineSeparator());
    }
}
