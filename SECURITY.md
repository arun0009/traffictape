# Security

TrafficTape is a **QA/staging** recorder, not a standing production logger.

## Reporting

Please open a GitHub issue or contact the maintainers privately for sensitive reports.
Do not attach a real captured tape that may contain credentials or PII.

## Defaults

- Capture is **disabled** until `traffictape.enabled=true`
- Authorization, Cookie, Set-Cookie, and common secret JSON fields are redacted
- `/health` and `/actuator/**` are excluded
- `multipart/form-data` and `application/octet-stream` are omitted
- Bodies are capped (`max-request-bytes` / `max-response-bytes`)
- `/actuator/traffictape` reports counts and routes only; still treat it as internal and do not expose it publicly

## What not to do

- Do not enable TrafficTape against production traffic that contains real PII unless you have tightened `capture.include` / `redaction` for that environment
- Do not commit captured tapes from real systems
- `output.console` writes redacted JSON to logger `traffictape.tape`. Route that logger on its own; do not mix it into the application log.
