This directory is a **hand-written sample tape** showing the v1 layout. Feed it to `traffictape-cli generate`.

```text
metadata.json       capture window, totals, captureReady
statistics.json     every endpoint/scenario count
gaps.json           ranked scenarios
fanout.json         typical outbound hops per inbound scenario
README.md           recorder writes this in a live capture
events/*.jsonl.gz   representative HTTP_TRANSACTION events
```

`events-000001.jsonl` is the uncompressed twin of the gzip file for easy reading.
