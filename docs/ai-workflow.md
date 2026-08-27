# AI / offline workflow

Mocks and a test plan are mechanical, so [`traffictape-cli`](generate.md) produces them directly — run it first. What is left is the judgement call: which scenarios deserve a regression test, and what each test should actually assert. That is what this workflow is for.

Give an LLM `test-plan.json` alongside your existing tests and it has the request, the expected response, and the outbound stubs each case depends on, without needing to parse the corpus itself.

## Inputs

1. `FOR_CLAUDE.md`
2. `test-plan.json` from `traffictape generate`
3. `statistics.json` / `gaps.json` / `fanout.json` (or latest CloudWatch `STATISTICS`)
4. Sampled `events/*.jsonl.gz` (or `HTTP_TRANSACTION` lines)
5. Existing Karate tests
6. Application source (optional)

## Prompt shape

```text
Analyze this real QA traffic corpus against the existing Karate tests.

Identify high-frequency and behaviorally significant request patterns
that are not adequately covered. Do not treat endpoint coverage as
scenario coverage: PATCH /assets/{id} with different request shapes
and statuses are different regression cases.

For each important gap:

1. Generate a Karate regression test.
2. Preserve meaningful assertions rather than snapshotting dynamic values.
3. Reuse the stubs already generated under wiremock/ or mountebank/ —
   test-plan.json names the ones each case depends on. Do not re-derive them.
4. Parameterize IDs, timestamps, tokens.
5. Do not duplicate identical scenario fingerprints.
6. Prefer representative scenarios over maximizing test count.
7. Call out cases whose expected body was omitted or truncated at capture
   time; those assertions cannot be trusted.
```

## CloudWatch dump

```text
filter eventType = "STATISTICS" | sort @timestamp desc | limit 1
filter eventType = "HTTP_TRANSACTION"
```

```text
aws logs filter-log-events --log-group-name /traffictape/qa/payments-api \
  --query 'events[*].message' --output text > events.jsonl
```
