# Security checklist

- No secrets, `local.properties`, credentials, tokens, or payloads are
  hardcoded or committed.
- No request/response bodies, authentication headers, or sensitive user data
  are logged.
- Inputs are validated before crossing the API boundary.
- User-visible failures use safe localized messages; internal diagnostics do
  not expose secrets.
- Dev cleartext networking is confined to the Dev overlay; Staging and Prod
  remain HTTPS-only.
- Delivery receipts and logs contain redacted, bounded evidence only.
- Workflow and credential changes are escalated as `HUMAN_ONLY` when required.
