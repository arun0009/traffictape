# traffictape-integration-tests

The end-to-end test, and the demo app it runs against. Not published to Maven Central.

`CaptureFromTestSuiteTest` boots a real Spring Boot app, drives HTTP through it, captures a tape, then runs the CLI over that tape and asserts the generated WireMock stubs and `test-plan.json` — including that an inbound case names the outbound stubs it depends on. It is the only test covering the whole loop, so treat a failure here as a product regression, not a flaky demo.

The same app doubles as the demo. From the repository root:

```bash
make demo      # capture over real HTTP, then generate mocks into target/demo
make example   # just run the app on port 18080
```

[Capture from tests](../../docs/capture-from-tests.md) · [Generate](../../docs/generate.md)
