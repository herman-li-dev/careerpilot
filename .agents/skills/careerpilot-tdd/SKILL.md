---
name: careerpilot-tdd
description: Implement CareerPilot behavior or reproducible bug fixes test-first in one small vertical slice. Use for backend or frontend feature work, behavior changes, and regression fixes; do not use for documentation-only edits or live-provider smoke tests.
---

# CareerPilot TDD

Follow the repository-root `AGENTS.md` first. Treat the relevant contract in `docs/` and existing public behavior as the boundary.

## Workflow

1. Select one externally observable behavior and the nearest existing public seam: controller endpoint, service method, SSE event, or Vue component behavior. Do not add a new architectural seam merely to enable testing.
2. State the acceptance check and add one deterministic test that fails for the intended reason.
3. Implement the minimum production change that makes that test pass.
4. Run the narrow test again, then the smallest existing broader check justified by the change.
5. Review the diff for unnecessary abstractions, dependencies, duplication, and unrelated edits. Simplify before finishing.

Proceed autonomously when the existing contract makes the seam clear. Ask only if choosing a seam would change a public API, database schema, dependency set, or product behavior.

## Test boundaries

- Backend: use the existing JUnit, Mockito, and Spring test support only as needed. Prefer a plain unit test when Spring context is unnecessary.
- Frontend: use only test commands and frameworks already defined in `package.json`. If no suitable runner exists, do not add one silently; verify with the existing build and report the missing automated coverage.
- Never call a live LLM, external network, or user account from a deterministic test. Use a stub, fake, or mock at the provider boundary.
- Do not enable the `external-tests` profile as part of normal TDD.
- Use only synthetic, non-sensitive resume and JD fixtures.

## Verification

- Record that the new test first failed for the expected reason and then passed.
- For backend changes, run the relevant test and then `.\mvnw.cmd test` on Windows when proportionate.
- For frontend changes, run the relevant existing check and `npm run build` when proportionate.
- If an external credential blocks only a live smoke test, complete all deterministic checks and report that smoke test separately.

Finish only when the requested behavior is covered at its public seam, the regression test is green, and unrelated safe checks remain green.
