This directory is a **hand-written sample corpus** showing the v1 layout. Read `FOR_CLAUDE.md` first, then feed the directory to `traffictape-cli generate`.

```text
metadata.json       capture window, totals, captureReady
statistics.json     every endpoint/scenario count (not only sampled bodies)
gaps.json           ranked scenarios; bodiesComplete = min(count, N) examples kept
fanout.json         typical outbound hops per inbound scenario
FOR_CLAUDE.md       read this first
events/*.jsonl.gz   representative HTTP_TRANSACTION events
```

`events-000001.jsonl` is the uncompressed twin of the gzip file for easy reading.

Inbound `ex-1` has outbound `sequence=1` with `parentExchangeId=ex-1`. The two PATCH events share an endpoint fingerprint and differ by request shape (`{status:string}` vs `{owner:string}`).
