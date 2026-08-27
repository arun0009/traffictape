# traffictape-example

Demo Spring Boot app that captures its own inbound requests and the outbound calls they make. Not published to Maven Central.

Use it to see the corpus and the generated stubs without touching your own service. From the repository root:

```bash
make demo      # capture over real HTTP, then generate mocks into target/demo
make example   # just run the app on port 18080
```

`make demo` drives a few requests, prints `/actuator/traffictape` readiness, and leaves the corpus in `target/demo/corpus` with stubs in `target/demo/mocks`.

[Capture from tests](../docs/capture-from-tests.md) · [Generate](../docs/generate.md)
