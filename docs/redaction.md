# Redaction and capture policy

Redaction runs **before** enqueue. Capture failures, including redaction failures, drop the example — they do not fail the app. Bodies are not written to the application log.

## Headers

Default denylist: `Authorization`, `Cookie`, `Set-Cookie`, `Proxy-Authorization`, `X-Api-Key`, `Api-Key`.

Values become `"[REDACTED]"`. Optional `capture.include.headers` turns this into an allow-list.

## JSON fields

Default denylist (case-insensitive, recursive): `password`, `token`, `accessToken`, `refreshToken`, `secret`, `clientSecret`, `ssn`, `creditCard`, `cardNumber`, `cvv`.

Optional `capture.include.json-fields` keeps only listed fields.

A JSON body that fails to parse — most often because it was truncated at `max-request-bytes` / `max-response-bytes` — cannot be field-redacted, so it is **omitted** rather than stored as raw text. `sizeBytes` is still recorded, so statistics and gaps are unaffected.

## XML and form-urlencoded bodies

The same field denylist is applied to non-JSON structured text:

- `application/xml`, `text/xml`, `*+xml` — leaf element text is replaced, including namespaced and attributed elements (`<ns:cvv type="str">123</ns:cvv>`). A denylisted element containing nested elements or CDATA is **not** matched; matching is deliberately linear to keep it off the critical path.
- `application/x-www-form-urlencoded` — `field=value` pairs are replaced.

Plain text (`text/plain`) has no field structure and is stored as-is after truncation.

Set `capture.text-bodies: false` to omit every non-JSON body regardless of type. Use this if you have XML payloads whose structure the element matcher above does not cover.

## Turning redaction off

`redaction.enabled: false` disables header, JSON, and text redaction entirely — bodies and headers are written verbatim, secrets included. A `WARN` is logged at startup when this happens. There is no reason to set it outside a fully synthetic environment.

## Routes, methods, content types, destinations

Default include methods: GET, POST, PUT, PATCH, DELETE (not HEAD/OPTIONS).

Default exclude routes: `/health`, `/actuator/**`.

Default exclude content types: `multipart/form-data`, `application/octet-stream`. Binary/image/audio/video are omitted by the body codec even if not listed.

## PII

TrafficTape cannot know your domain’s PII fields. Add them to `redaction.json-fields` before enabling in QA. Prefer omission over hoping a field name matches.
