package io.traffictape.cli;

import io.traffictape.TrafficTapeVersion;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Offline entry point. Reads a corpus and writes mock definitions; it never talks to a network.
 */
public final class TrafficTapeCli {

    private TrafficTapeCli() {
    }

    public static void main(String[] args) {
        int status = run(args, System.out, System.err);
        if (status != 0) {
            System.exit(status);
        }
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0 || isHelp(args[0])) {
            usage(out);
            return args.length == 0 ? 2 : 0;
        }
        if ("version".equals(args[0])) {
            out.println("traffictape " + TrafficTapeVersion.get());
            return 0;
        }
        if (!"generate".equals(args[0])) {
            err.println("Unknown command: " + args[0]);
            usage(err);
            return 2;
        }
        try {
            Options options = Options.parse(args);
            return new GenerateCommand().execute(options, out);
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            usage(err);
            return 2;
        } catch (IOException e) {
            err.println("Failed: " + e.getMessage());
            return 1;
        }
    }

    private static boolean isHelp(String arg) {
        return "-h".equals(arg) || "--help".equals(arg) || "help".equals(arg);
    }

    private static void usage(PrintStream out) {
        out.println("""
                traffictape generate --corpus <path> [options]

                  Reads a TrafficTape corpus and writes mock definitions for the outbound calls the
                  recorded application made, plus a test plan for the inbound requests it served.

                  --corpus <path>   Corpus directory, its events/ directory, or a single
                                    .jsonl/.jsonl.gz file (for example a CloudWatch dump).
                  --out <dir>       Output directory. Default: ./traffictape-out
                  --format <f>      wiremock | mountebank | both. Default: both
                  --base-port <n>   First port for Mountebank imposters. Default: 4545
                  --help            This message.

                Scenarios are deduplicated by fingerprint, so a corpus collected from several
                instances yields one stub per distinct behaviour.""");
    }

    record Options(Path corpus, Path out, boolean wiremock, boolean mountebank, int basePort) {

        static Options parse(String[] args) {
            Path corpus = null;
            Path out = Path.of("traffictape-out");
            String format = "both";
            int basePort = 4545;

            for (int i = 1; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--corpus" -> corpus = Path.of(value(args, ++i, "--corpus"));
                    case "--out" -> out = Path.of(value(args, ++i, "--out"));
                    case "--format" -> format = value(args, ++i, "--format");
                    case "--base-port" -> basePort = port(value(args, ++i, "--base-port"));
                    default -> throw new IllegalArgumentException("Unknown option: " + arg);
                }
            }
            if (corpus == null) {
                throw new IllegalArgumentException("--corpus is required");
            }
            List<String> formats = List.of("wiremock", "mountebank", "both");
            if (!formats.contains(format)) {
                throw new IllegalArgumentException(
                        "--format must be one of " + formats + ", got: " + format);
            }
            boolean wiremock = "wiremock".equals(format) || "both".equals(format);
            boolean mountebank = "mountebank".equals(format) || "both".equals(format);
            return new Options(corpus, out, wiremock, mountebank, basePort);
        }

        private static String value(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException(option + " requires a value");
            }
            return args[index];
        }

        private static int port(String raw) {
            try {
                int parsed = Integer.parseInt(raw);
                if (parsed < 1 || parsed > 65535) {
                    throw new NumberFormatException(raw);
                }
                return parsed;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("--base-port must be a port number, got: " + raw);
            }
        }
    }
}
