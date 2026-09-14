---
name: careerpilot-diagnosing
description: Diagnose CareerPilot startup, DashScope, SSE, Spring Boot, PostgreSQL, Vue, build, test, or runtime failures with a small redacted reproduction. Use for errors, failing tests, broken behavior, and regressions; diagnose without editing unless the user also asks for a fix.
---

# CareerPilot Diagnosing

Follow the repository-root `AGENTS.md` first. Diagnosis is read-only unless the user explicitly requests a fix or implementation.

## Safety first

- Never print, request, persist, or echo API keys, tokens, passwords, identity documents, payment details, or personal resume data.
- Check whether a secret environment variable exists, not its value.
- Redact authorization headers, query credentials, provider request bodies, and sensitive response bodies before reporting evidence.
- Do not invoke application-facing shell, download, or unrestricted filesystem tools.

## Diagnostic loop

1. Classify the failure: configuration/startup, model provider, HTTP/SSE, database, frontend/build, or test isolation.
2. Capture the smallest reliable failing signal: a specific test, compile error, local endpoint response, browser/network event, or the relevant `ERROR` and nested `Caused by` lines.
3. Reproduce with the narrowest safe command. Prefer synthetic data and local stubs. Do not enable `external-tests` or make a real provider request unless the requested task is explicitly a live smoke test.
4. Form one evidence-based hypothesis at a time. Inspect or instrument only what distinguishes that hypothesis from alternatives.
5. Minimize the reproduction and identify the first failing boundary, not merely the final exception.
6. Report the root cause, supporting evidence, affected scope, and smallest next action.

For DashScope failures, distinguish local configuration, region/base URL, model authorization, account risk control or billing, request format, and startup-time RAG initialization. Do not treat an account-side rejection as a code defect without evidence.

## If a fix is requested

- Make one localized patch after establishing the failing signal.
- Add or update a deterministic regression test when feasible.
- Re-run the narrow reproduction, then the relevant repository verification.
- Do not add infrastructure, dependencies, abstractions, or broad retries to mask an unknown cause.

Stop and give one exact safe user action when progress requires account approval, a dashboard setting, or a secret available only to the user. Never ask the user to paste the secret into chat or source code.

Diagnosis is complete when the failure has a reproducible signal and evidence-backed root cause, or when the remaining blocker is precisely identified as external.
