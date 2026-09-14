# CareerPilot Agent Guidelines

## Project context

- CareerPilot is an evidence-grounded resume and job preparation application.
- Backend: Java 21, Spring Boot 3.4.4, Maven Wrapper, Spring AI Alibaba.
- Frontend: Vue 3 and Vite.
- Keep the application a modular monolith. PostgreSQL is the planned primary database; pgvector may be enabled only when the RAG milestone begins.
- Current delivery order is: preserve and run the original chat/SSE flow, then add the CareerPilot business flow in small, runnable increments.

## Simplicity and architecture budget

- Prefer the simplest solution that satisfies the current acceptance criteria.
- Modify existing code before adding layers, frameworks, modules, or services.
- Do not design for hypothetical future requirements. Avoid speculative extension points and abstractions used only once.
- Do not introduce Redis, message queues, microservices, API gateways, service discovery, distributed transactions, CQRS, event-driven architecture, Kubernetes, or observability stacks unless the user explicitly requests them and a concrete current problem justifies them.
- Do not add a production dependency until explaining why the existing JDK, Spring Boot, Vue, or current dependencies cannot solve the requirement and what operational cost the dependency adds.
- Avoid single-implementation interfaces, pass-through services, one-use helpers, unnecessary factories or strategies, and DTO/VO/BO/DO conversions that add no clear boundary or validation value.
- Do not upgrade Java, Spring Boot, Spring AI, Vue, Vite, or other major dependencies as part of an unrelated task.

## Scope and change control

- For review, explanation, or diagnosis requests, inspect and report without modifying files.
- For implementation requests, make one coherent, independently verifiable change at a time.
- Preserve existing API contracts, database schemas, and user-visible behavior unless the task explicitly requires changing them.
- If a change would add a production dependency, alter a database schema, change a public API, or spread across more than five existing files, explain why and propose the smallest viable slice before editing.
- Do not perform repository-wide refactors, mass renames, formatting sweeps, or generated-code rewrites unless explicitly requested.
- Preserve unrelated user changes. Do not reset, discard, commit, push, or change branches unless explicitly requested.

## Autonomous execution

- Within the user's requested scope, proceed without asking for confirmation for read-only inspection, localized reversible edits, and relevant tests.
- When several simple solutions are valid, choose the least invasive one and continue.
- Make ordinary implementation decisions that preserve existing dependencies, API contracts, database schemas, and user-visible behavior.
- Explaining a change does not require waiting for approval unless it introduces a material tradeoff or expands the requested scope.
- Pause for user direction only when a decision changes product behavior, adds operational infrastructure, introduces a production dependency, changes a public API or database schema, or risks destructive or irreversible effects.

## Required working loop

1. Inspect the relevant code and current behavior, using `sentry` when repository investigation should be delegated.
2. State the smallest intended change and its acceptance check.
3. Implement only that slice, using `laborer` when the implementation forms a clear bounded unit.
4. Review the resulting diff and run the narrowest relevant verification, then broader tests when justified.
5. Report changed files, behavior changes, tests run, and any remaining risk or blocker.

After implementation, review the diff specifically for unnecessary classes, abstractions, duplication, dependencies, and speculative future-proofing. Prefer deleting unnecessary code over adding another layer.

## Build and verification

- Use the Maven Wrapper from the repository root. The backend requires Java 21.
- Backend verification command: `.\mvnw.cmd test` on Windows.
- Frontend verification command: `npm run build` from `careerpilot-frontend`.
- Do not invent a test command that is not defined in `package.json` or the Maven build.
- Tests must not depend on live LLM calls when a deterministic stub or mock can verify the behavior.
- If credentials or an external service prevent a verification step, run all unaffected checks and report the exact blocked check without exposing secrets.

## Security and data handling

- Never write API keys, passwords, tokens, identity documents, payment details, or personal resume data into source files, logs, tests, fixtures, documentation, or chat output.
- Load secrets from environment variables. Keep committed configuration limited to placeholders or safe defaults.
- Use only synthetic or explicitly anonymized resumes and job descriptions in fixtures and examples.
- Do not expose arbitrary shell execution, arbitrary file download, or unrestricted filesystem tools through application-facing AI features.
- Treat model output as untrusted input: validate structured output and fail safely before persistence or display.

## CareerPilot product boundaries

- V1 focuses on one flow: resume text plus job-description text produces a structured match report and a practical preparation plan.
- Start with pasted text. Add PDF/DOCX upload only after the text flow is stable.
- Keep raw input and validated structured results separate when persistence is introduced.
- RAG, pgvector, two-stage questions, and plan automation are later milestones; they must not block the first resume/JD matching loop.
- Prefer fixed DTO or record fields for AI business output over free-form Markdown where the application consumes the result.

## Completion report

Every completed coding task should state:

- files changed and approximate scope;
- whether dependencies, database schema, or API contracts changed;
- verification commands and results;
- known limitations and the smallest reasonable next step.

## Subagent routing

### Root model gate

- When the root model is `gpt-5.6-sol`, apply the routing rules below when deciding whether to delegate work.
- For other root models, execute directly by default.
- An explicit user request to use a subagent or parallelize work overrides the default routing behavior.
- Delegation does not change the main agent's responsibility for scope, architecture, integration, final verification, and user communication.

### sentry

- Delegate primarily read-only repository exploration to the `sentry` subagent.
- Typical `sentry` work includes:
  - locating relevant files and symbols;
  - tracing request flows, execution paths, and call chains;
  - inspecting dependencies, configuration, logs, tests, and existing behavior;
  - identifying the smallest set of files related to the task;
  - collecting factual evidence before an implementation decision.
- `sentry` must remain read-only and must not modify files.
- Prefer `sentry` when the task is investigation-heavy or when the implementation path is not yet clear.
- The main agent must integrate `sentry` findings and make the implementation or architecture decision.

### Exploration before implementation

- When a task requires modifying existing code but the relevant files, execution path, impact scope, or current behavior are not sufficiently clear, delegate read-only investigation to `sentry` first.
- Ask `sentry` to return:
  - relevant files and key symbols;
  - the actual execution path;
  - existing tests and validation paths;
  - constraints and preserved behavior;
  - unresolved questions or risks.
- Use those findings to choose the smallest defensible implementation slice.
- Once the implementation boundary is clear, delegate the bounded code change to `laborer` when appropriate.
- Do not perform unnecessary repository-wide exploration for simple, localized, already-understood changes.

### laborer

- Delegate bounded implementation work to the `laborer` subagent when the user has authorized code changes and the work forms a clear, independently verifiable unit.
- Typical `laborer` work includes:
  - feature implementation;
  - bug fixes;
  - focused refactoring;
  - unit or integration tests;
  - medium-complexity debugging that requires code changes.
- Every delegation to `laborer` must specify:
  - the concrete goal;
  - acceptance criteria;
  - allowed files or modules;
  - behavior that must be preserved;
  - relevant test, lint, or build commands;
  - known user changes or files that must not be touched.
- `laborer` must follow the project's simplicity, scope-control, security, dependency, API, database, and verification rules in this file.
- `laborer` must not expand the requested scope on its own.
- `laborer` must not perform destructive operations, commit, push, create a pull request, deploy, change branches, or discard user changes unless explicitly authorized.
- After `laborer` finishes, the main agent must review the diff, handle integration concerns, and run or confirm final verification.

### Do not delegate to laborer when

- The user only requested review, explanation, diagnosis, or architectural discussion and did not authorize file modification.
- The change is so small that delegation would add more coordination cost than implementation value.
- Product behavior, architecture, public API, database schema, production dependency, or another material tradeoff still requires a main-agent decision or user direction.
- The implementation scope is not clear enough to assign explicit file or module ownership.
- The work would overlap with files currently owned by the main agent or another implementation subagent.
- The task involves secrets, production systems, destructive actions, or external writes without explicit authorization.

### Parallel delegation

- Parallelize only independent work with non-overlapping file or module ownership.
- Prefer splitting work by module, test scope, or independently verifiable deliverable rather than by arbitrary small steps.
- A file or tightly coupled module may be owned by only one implementation agent at a time.
- The main agent must not edit files currently assigned to `laborer`.
- Do not parallelize when coordination cost is likely to exceed the expected benefit.
