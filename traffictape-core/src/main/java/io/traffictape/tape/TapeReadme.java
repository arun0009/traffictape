package io.traffictape.tape;

/** Read-this-first note written next to the recorded events. */
public final class TapeReadme {

    public static final String FILENAME = "README.md";

    public static final String TEXT = """
            # TrafficTape — read this first

            Observed HTTP, not a spec and not generated tests.

            - `statistics.json` — every scenario seen; counts continue after bodies stop.
              `captureReady` = no new scenario for `plateau-after`.
            - `gaps.json` — ranked; `bodiesComplete` = min(count, N) examples kept.
            - `fanout.json` — outbound hops per inbound scenario (mocks).
            - `events/*.jsonl.gz` — sampled request/response bodies.

            One test per **scenario**, not per endpoint. Mocks from `fanout.json`
            or `parentExchangeId` + `sequence`. Parameterize ids, timestamps,
            tokens; never snapshot secrets. `correlation.onDemandTag` marks a
            request recorded deliberately (a bug reproduction) — test those first.
            """;

    private TapeReadme() {
    }
}
