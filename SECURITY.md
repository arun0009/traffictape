# Security

TrafficTape is designed to observe **QA/staging** traffic, not to become a standing production logger.

## Reporting

Please open a GitHub issue or contact the maintainers privately for sensitive reports.
Do not attach real captured corpora that may contain credentials or PII.

## Defaults

- Capture is **disabled** until `traffictape.enabled=true`
- Authorization, Cookie, Set-Cookie, and common secret JSON fields are redacted
- `/health` and `/actuator/**` are excluded
- `multipart/form-data` and `application/octet-stream` are omitted
- Bodies are capped (`max-request-bytes` / `max-response-bytes`)

## What not to do

- Do not enable TrafficTape against production traffic that contains real PII unless you have tightened `capture.include` / `redaction` for that environment
- Do not commit captured corpora from real systems
- Do not log request/response bodies to the application logger
