# Capture from your existing test suite

The fastest way to get a corpus is to point TrafficTape at the tests you already have. One `mvn test`, no deployment, no waiting for QA traffic, and no review of whether customer data may be written to disk — the only traffic is traffic your own tests generated.

Use this to evaluate TrafficTape in a few minutes, and to lock in current behaviour before a refactor. Use QA capture (see the README) when you need the scenarios your tests *don't* cover.

## Recipe

Add the starter in test scope so it cannot ship. Pin `${traffictape.version}` to the Maven Central badge.

```xml
<dependency>
    <groupId>io.github.arun0009</groupId>
    <artifactId>traffictape-spring-boot</artifactId>
    <version>${traffictape.version}</version>
    <scope>test</scope>
</dependency>
```

Enable capture for tests only — `src/test/resources/application.yml`, or a profile you activate on the capture run:

```yaml
traffictape:
  enabled: true
  # A handful of examples per scenario is plenty; a suite repeats itself far more than QA does.
  max-examples-per-scenario: 3
  output:
    directory: target/traffic-tape   # under target/, so `mvn clean` disposes of it
  flush:
    interval: 1s
    max-events: 50
```

Run the suite, then generate:

```bash
mvn test
java -jar traffictape-cli-${traffictape.version}-all.jar generate --corpus target/traffic-tape --out ./out
```

That is the whole loop. `out/wiremock/mappings` holds a stub per outbound call your service made, and `out/test-plan.json` holds a case per inbound request naming the stubs it depends on. See [generate](generate.md).

## What actually gets captured

Inbound capture is a servlet filter, so it records Spring MVC and JAX-RS/Jersey on the same container. Outbound capture is client interceptors: injected `RestClient.Builder` / `RestTemplateBuilder` / `WebClient.Builder`, an `OkHttpClient` bean, or a JAX-RS `Client` bean.

This means the test style matters more than the test count:

| Test style | Inbound | Outbound |
|---|---|---|
| `@SpringBootTest(webEnvironment = RANDOM_PORT)` over real HTTP | yes | yes |
| `@SpringBootTest` + `MockMvc` | yes | yes, if the client is a real one |
| `@WebMvcTest` with mocked collaborators | yes | nothing real to record |
| Plain unit tests, no servlet container | no | no |

Tests that stub the HTTP client (`MockRestServiceServer`, Mockito on a client wrapper) produce no outbound events, because no HTTP call happens. Those endpoints will show up in `gaps.json` with no dependencies — which is itself useful: it tells you which collaborators you have never exercised for real.

## Knowing when the corpus is complete

The writer is asynchronous, so events are on disk once the context shuts down and the worker drains. For a normal run that happens for free: the JVM exits at the end of `mvn test`, Spring closes the context, and the worker flushes.

If you want to assert on the corpus *inside* a test, close the worker yourself first:

```java
@Autowired AsyncCaptureWorker worker;

@Test
void capturesTraffic() throws Exception {
    // ... drive requests ...
    worker.close();   // drains the queue and closes the sink
    // ... now read target/traffic-tape ...
}
```

Pair that with `@DirtiesContext`, since the context is no longer capturing afterwards. A worked example lives in `tests/traffictape-integration-tests/src/test/java/io/traffictape/example/CaptureFromTestSuiteTest.java`, and it runs as part of this repository's build.

## Many contexts, one corpus

A suite usually builds several application contexts, and each gets its own sink writing to the same directory. That is fine: sinks resume numbering after the events files already present, and create files exclusively, so contexts and parallel Surefire forks append rather than overwrite each other. `generate` reads every file in `events/`.

Two consequences worth knowing:

- **Sampling budgets are per context.** `max-examples-per-scenario` counts within one context, so a scenario exercised by three test classes with three separate contexts can yield up to three times the budget. Deduplication in `generate` collapses these, so it affects corpus size, not output.
- **The corpus accumulates across runs.** A second `mvn test` without `mvn clean` appends to the first. Delete the directory when you want a clean read.

## The inverse: keeping test traffic out of a QA corpus

Here test traffic is the point. On a QA deployment it usually is not: functional tests that run on startup, smoke suites, and uptime probes hit the same endpoints as real users, so excluding them by route is not an option. Mark them with a header at the source and exclude on it:

```yaml
traffictape:
  capture:
    exclude:
      request-headers:
        X-Smoke-Test: ["*"]
```

The outbound calls those requests caused are dropped too, so you do not end up with dependencies that have no parent request. Details in [configuration](configuration.md#excluding-synthetic-traffic).

## Limits

The corpus is exactly as good as the suite that produced it. If your tests never exercise the error paths, no error scenarios appear, and `generate` will happily produce a green-path-only test plan. That is the honest baseline for a refactor — it pins the behaviour you currently verify — but it is not evidence about behaviour nobody tests. `gaps.json` is where you look for what is missing before trusting it.
