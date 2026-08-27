package io.traffictape.corpus;

/** Brief dropped in the corpus so Claude knows what to read first. */
public final class ForClaude {

    public static final String FILENAME = "FOR_CLAUDE.md";

    public static final String TEXT = """
            # TrafficTape corpus — read this first

            Observed QA HTTP, not a spec and not generated tests.

            - `statistics.json` — every scenario seen (counts continue after bodies stop). `captureReady` = no new scenario for `plateau-after`.
            - `gaps.json` — ranked; `bodiesComplete` = min(count, N) examples kept.
            - `fanout.json` — typical outbound hops per inbound scenario (mocks).
            - `events/*.jsonl.gz` — sampled request/response bodies.

            One regression test per **scenario**, not per endpoint. Mocks = `fanout.json` or `parentExchangeId` + `sequence`. Parameterize ids, timestamps, tokens; do not snapshot secrets.

            CloudWatch: `filter eventType = "STATISTICS"` (latest) and `filter eventType = "HTTP_TRANSACTION"`.
            """;

    private ForClaude() {
    }
}
