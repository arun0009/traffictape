# Sampling

The sampler keys on a **scenario**, not an endpoint and not a 2xx/4xx quota table.

```text
ScenarioKey = endpoint fingerprint + request body shape + response characteristic
```

Response characteristic is `{status}` or `{status}:empty`. These are independent budgets:

```text
GET /accounts/{id} + none + 200
GET /accounts/{id} + none + 200:empty
GET /accounts/{id} + none + 404
PATCH /assets/{id} + {status:string} + 200
PATCH /assets/{id} + {owner:string} + 200
```

v0.1: **first N** per scenario (`max-examples-per-scenario`, default 50). Bodies stop at N; counts continue. New scenarios get their own N. `Sampler` is the extension point.

Endpoint stats rank volume; scenario stats rank shapes.
