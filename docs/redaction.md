# Redaction and capture policy

Redaction runs **before** enqueue. Capture failures, including redaction failures, drop the example — they do not fail the app. Bodies are not written to the application log.

## Headers

Default denylist: `Authorization`, `Cookie`, `Set-Cookie`, `Proxy-Authorization`, `X-Api-Key`, `Api-Key`.

Values become `"[REDACTED]"`. Optional `capture.include.headers` turns this into an allow-list.

## JSON fields

Default denylist (case-insensitive, recursive): `password`, `token`, `accessToken`, `refreshToken`, `secret`, `clientSecret`, `ssn`, `creditCard`, `cardNumber`, `cvv`.

Optional `capture.include.json-fields` keeps only listed fields.

## Routes, methods, content types, destinations

Default include methods: GET, POST, PUT, PATCH, DELETE (not HEAD/OPTIONS).

Default exclude routes: `/health`, `/actuator/**`.

Default exclude content types: `multipart/form-data`, `application/octet-stream`. Binary/image/audio/video are omitted by the body codec even if not listed.

## PII

TrafficTape cannot know your domain’s PII fields. Add them to `redaction.json-fields` before enabling in QA. Prefer omission over hoping a field name matches.
