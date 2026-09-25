# CareerPilot V1 Implementation Backlog

Status: V1 implemented and accepted; public synthetic demo verified through DEPLOY-02 on 2026-09-11
Product language: English
Architecture: modular Spring Boot monolith with one PostgreSQL database

## 1. Working rule

Complete one task at a time. Each task must leave the application compilable, run the narrowest
relevant tests, and receive a diff review before the next task starts. Do not combine tasks merely
because they belong to the same milestone.

A task may introduce only the dependency, schema, API, or UI change stated in that task. Redis,
message queues, microservices, unbounded RAG, and autonomous tools are not part of this backlog. U-01 adds
request-only PDF/DOCX Resume upload; RAG-01 later adds one explicitly bounded public-synthetic pgvector slice.

## 2. Current baseline

Completed Stage 0 engineering work:

- Java 21 build confirmed;
- secrets loaded from environment variables;
- obsolete RAG startup initialization disabled by default;
- obsolete public model-tool routes disabled;
- the initial SSE implementation receives an explicit completion marker;
- prompt, response, tool-argument, and tool-result logging removed;
- CORS restricted to configured application origins;
- external and side-effect tests excluded from the default Maven test suite;
- backend safe tests and frontend production build pass.

### `S0-01` — Manual live chat and SSE smoke test — Completed

Scope: Start the backend with a temporary China-region DashScope key, start the frontend, and exercise
one synthetic streaming response through the initial UI.

Acceptance:

- response text arrives incrementally;
- the UI treats completion as normal rather than as an error;
- no API key or message body appears in logs;
- provider/account errors, if any, are captured without secrets.

Verified on 2026-08-30 with a temporary China-region DashScope key:

- response text arrived incrementally;
- the stream ended with exactly one `[DONE]` marker;
- the browser treated completion as normal rather than as an error;
- the key and message body were not emitted by application logging.

Stage 0 is complete. The next implementation task is `F-01`.

## 3. Foundation and persistence

### `F-01` — Add PostgreSQL migration support — Completed

Scope: Add Flyway, local datasource configuration through environment variables, and one empty
database connectivity test. Do not create business tables yet.

Expected material change: the Flyway dependency/module required by the project's managed versions
and database configuration. Before implementation, confirm the selected PostgreSQL version and
local connection values; do not guess dependency versions.

Acceptance:

- application starts against an empty PostgreSQL database;
- Flyway reports a clean schema history;
- no credentials are committed;
- failure to connect returns a clear startup error.

Verified on 2026-08-30 with PostgreSQL 16.15, Flyway 10.20.1, and PostgreSQL JDBC 42.7.5:

- the application connected to an empty `careerpilot` database;
- Flyway applied the comment-only V1 baseline and recorded a clean successful schema history;
- the external connectivity test passed without storing credentials;
- an unavailable database produced an explicit startup failure with SQL state `08001`.

The next implementation task is `F-02`.

### `F-02` — Add validation and the API error envelope — Completed

Scope: Add request validation, the documented success/error envelope, and global exception mapping for
CareerPilot endpoints.

Expected material change: `spring-boot-starter-validation` production dependency and new response
behavior only for CareerPilot endpoints.

Acceptance:

- a controller-slice test fixture returns the documented envelope without adding a throwaway production endpoint;
- invalid fields return stable English field errors;
- stack traces and provider details are not returned.

Verified on 2026-08-30:

- a test-only controller fixture returns the documented success envelope;
- blank fields and malformed JSON return stable English `VALIDATION_ERROR` responses;
- unexpected exceptions return a safe `INTERNAL_ERROR` without exposing internal details;
- exception handling is scoped to CareerPilot controllers;
- the focused MVC contract tests and the complete default test suite pass.

The next implementation task is `I-01`.

## 4. Identity and ownership

### `I-01` — Create user and profile schema — Completed

Scope: Add the `app_user` and `user_profile` Flyway migration exactly as defined in the data model.
Do not add roles, refresh tokens, permissions tables, or social login.

Acceptance:

- migration works on a clean database and on an already migrated database;
- normalized email uniqueness is enforced;
- password hashes, never passwords, are stored.

Verified on 2026-08-30:

- Flyway V2 applies cleanly and creates `app_user` and `user_profile`;
- normalized email uniqueness and normalized-storage checks are enforced;
- `app_user` exposes `password_hash` and has no plaintext `password` column;
- profile ownership and weekly-hours constraints are covered by PostgreSQL integration tests;
- test records are transactionally rolled back, leaving both tables empty after verification.

The next implementation task is `I-02`.

### `I-02` — Implement registration and password verification — Completed

Scope: Implement registration repository/service/controller code and password hashing. Add the
smallest Spring Security dependency required for a maintained password encoder.

Acceptance:

- registration and duplicate-email tests pass;
- passwords never appear in responses or logs;
- no JWT or authorization filter is added in this task.

Verified on 2026-08-30:

- `POST /api/auth/register` returns `201` with public user fields only;
- email trimming and lowercasing occur before persistence;
- BCrypt hashes, never raw passwords, are written to `app_user.password_hash`;
- duplicate normalized emails return `409 EMAIL_ALREADY_REGISTERED`;
- correct, incorrect, and missing-account password verification paths are covered;
- controller, service, full default, and isolated PostgreSQL integration tests pass.

The next implementation task is `I-03`.

### `I-03` — Implement login cookie, logout, and current user — Completed

Scope: Add signed JWT cookie authentication for the endpoints defined in `api-contract-v1.md`.
Choose maintained Spring Security JWT support instead of custom cryptography, and document the
dependency before adding it.

Acceptance:

- login, logout, expiration, invalid-signature, and current-user tests pass;
- cookie flags match local and production environments;
- tokens never enter browser storage or query strings.

Verified on 2026-08-30:

- login issues an HS256-signed JWT in the `careerpilot_session` cookie;
- cookies are `HttpOnly`, `SameSite=Strict`, scoped to `/api`, and configurable as `Secure`;
- production secure-cookie configuration requires an external signing secret;
- logout expires the cookie with `204 No Content`;
- `/api/users/me` returns the active user and optional career profile;
- missing, expired, and invalid-signature cookies return stable `401` responses;
- tokens are absent from JSON bodies, browser storage, and query strings;
- focused JWT/Cookie tests and isolated PostgreSQL authentication integration tests pass.

The next implementation task is `I-04`.

### `I-04` — Prove ownership isolation — Completed

Scope: Add reusable current-user lookup and integration tests that demonstrate User A cannot read
or modify User B's records. Do not create a generic permission framework.

Acceptance: unauthenticated access returns `401`; cross-user IDs return `404`; same-user access works.

Verified on 2026-08-31:

- CareerPilot controllers can request the authenticated identity with the typed `@CurrentUserId`
  parameter instead of reading a string request-attribute name;
- all `/api/users/**` routes require a valid signed session cookie;
- missing authentication returns stable `401 AUTHENTICATION_REQUIRED`;
- PostgreSQL integration tests use an owned `user_profile` record to prove that User A receives
  `404 RESOURCE_NOT_FOUND` when reading or modifying User B's record;
- the rejected cross-user update leaves User B's row unchanged, while same-user reads and updates work;
- the ownership predicates follow `resource.id + resource.user_id = currentUser.id`; no generic
  permission framework, new table, or production test endpoint was added.

The next implementation task is `D-01`.

## 5. Resume and job-description flow

### `D-01` — Create resume and job-description schema — Completed

Scope: Add only the `resume` and `job_description` tables, constraints, and known indexes from the
data model.

Acceptance: migration and constraint tests pass; no file-upload columns or embedding columns exist.

Verified on 2026-08-31:

- Flyway V3 upgrades the existing identity schema and creates only `resume` and `job_description`;
- required ownership, non-blank text, and parse-status constraints are enforced;
- known `(user_id, created_at)` indexes exist in the documented column order;
- both tables default to `NOT_STARTED` and contain no file-upload, document-chunk, or embedding columns;
- the focused PostgreSQL migration tests and the complete default test suite pass.

The next implementation task is `D-02`.

### `D-02` — Implement pasted-text CRUD — Completed

Scope: Implement create, list, get, and protected delete for resumes and job descriptions. Parsing
remains `NOT_STARTED` in this task.

Acceptance:

- all text is owned by the authenticated user;
- lists are newest first;
- blank text is rejected in English;
- cross-user access returns `404`.

Verified on 2026-08-31:

- authenticated users can create, list, read, and delete their pasted resumes and job descriptions;
- creates persist raw text unchanged with `parseStatus = NOT_STARTED` and no parse attempt;
- lists are newest first, blank required fields return stable English validation errors, and all
  resource queries include the current user ID;
- a different user's get or delete receives `404 RESOURCE_NOT_FOUND` without changing the record.

The next implementation task is `D-03`.

### `D-03` — Add deterministic parsing boundary — Completed

Scope: Define the smallest parsing component that converts raw text to validated structured data.
Production uses the existing Spring AI client; tests inject a deterministic fake. Do not create a
general agent framework.

Acceptance:

- valid output stores `parsed_json` and `COMPLETED`;
- invalid output stores `FAILED` without changing `raw_text`;
- retry endpoints work for owned resources;
- default tests make no live model calls.

Verified on 2026-08-31:

- `DocumentParser` is the narrow model boundary: production uses the existing Spring AI client,
  while integration tests provide a deterministic fake;
- exact, fixed JSON shapes are validated before either `parsed_json` column is updated;
- successful parses move the owned resource to `COMPLETED`, while malformed output or parser failure
  leaves `raw_text` unchanged and records a safe `FAILED` state;
- owned parse retries work only from `NOT_STARTED` or `FAILED`; completed or active resources return
  `409 INVALID_RESOURCE_STATE`, and another user's resource returns `404`;
- default tests make no model request, and focused PostgreSQL integration tests pass without a live provider.

The next implementation task is `D-04`.

### `D-04` — Add the English resume/JD UI — Completed

Scope: Replace the initial prototype home flow with authenticated English pages for pasted resumes
and job descriptions. Keep styling modest and reuse the existing Vue stack.

Acceptance: a user can create, list, open, and retry parsing from the browser; no file upload exists.

Verified on 2026-08-31:

- the root route now presents an English CareerPilot workspace with registration and cookie-based sign-in;
- authenticated users can create, list, open, and parse or retry their owned resumes and job descriptions;
- API errors are shown as safe English messages and pasted text remains in the form when saving fails;
- no token is stored in browser storage and no file-upload control or endpoint is introduced;
- the frontend production build passes.

## 6. Match-report loop

### `R-01` — Create analysis schema and report DTO — Completed

Scope: Add `analysis_report`, its lifecycle enum mapping, and the fixed report record/DTO. Do not
add follow-up or plan behavior yet beyond the nullable columns already defined.

Acceptance: database constraints enforce score 0–100; JSON validation tests cover every fixed field.

Verified:

- Flyway V4 creates `analysis_report` with the documented ownership and lifecycle constraints;
- the persisted score is constrained to 0–100;
- `MatchReport` uses the fixed report shape and rejects malformed or out-of-range output.

### `R-02` — Implement report generation with a fake model — Completed

Scope: Implement the service flow from two owned parsed inputs to a persisted report. Use bounded
retry for invalid structured output and a deterministic fake in tests.

Acceptance:

- lifecycle transitions are persisted;
- malformed output and timeout become safe `FAILED` states;
- no invented evidence is accepted;
- rerunning creates a new immutable historical analysis.

Verified:

- report generation uses a narrow replaceable model boundary and bounded validation retries;
- capability matching is normalized to the fixed taxonomy and the estimated score is recalculated locally;
- unsupported evidence is removed before persistence;
- every run creates a separate historical report, and default tests make no live model calls.

### `R-03` — Add analysis REST endpoints — Completed

Scope: Implement create/start, list, and get endpoints. Keep model execution in the current process;
do not add a queue or scheduler.

Acceptance: documented status codes, ownership checks, newest-first history, and error envelope pass;
an application restart marks abandoned `PENDING` or `RUNNING` work as safely retryable `FAILED` state.

Verified:

- authenticated create, list, and get endpoints use owned completed inputs;
- history is newest first and another user's ID returns `404`;
- startup recovery marks abandoned in-process work as `FAILED` with a safe interruption error.

### `R-04` — Add CareerPilot SSE and report UI — Completed

Scope: Implement persisted analysis events and an English report page. The report-only sequence is
`progress → report → done`.

Acceptance:

- event order and exactly one `done` per successful connection are tested;
- reconnect replays persisted state without duplicating work;
- disconnect does not change the database lifecycle;
- the browser displays every fixed report field.

Verified:

- completed-state replay emits `progress → report → done` exactly once per connection;
- plan-enabled replay emits `progress → report → plan → done`;
- persisted state remains the source of truth across refresh and reconnect;
- the English UI displays report history and every fixed report field.

## 7. Follow-up question — Skipped for V1

### `Q-01` — Persist one focused question — Skipped

The proposed question asked for a target city. Resume/JD matching already receives the role's location
from the saved job description, and a separate target-city answer does not materially change the current
report or evidence-based preparation plan. Adding a waiting state and question persistence would therefore
add workflow complexity without a V1 product need.

`question_context_json` and `WAITING_FOR_USER_INPUT` remain unused reserved schema values; no question is
created during the V1 analysis flow.

### `Q-02` — Accept an answer and resume analysis — Skipped

No answer endpoint or question form is implemented because Q-01 has no current product trigger. A future
question flow requires a new concrete decision that cannot be derived from the resume, job description,
or optional profile; it must not be added merely to collect target city.

## 8. Preparation plan

### `P-01` — Create plan and task schema — Completed

Scope: Add `career_plan` and `plan_task` exactly as documented. Do not add reminders, calendars, or
notifications.

Acceptance: constraints and derived ownership tests pass.

Verified: Flyway V5 creates `career_plan` and `plan_task`; Flyway V6 adds task archiving and the active-task
index. Task ownership is derived through the owned plan.

### `P-02` — Generate and persist a 14-day plan — Completed

Scope: Generate validated English tasks from one completed report and save the plan plus tasks in
one transaction.

Acceptance: every task has source evidence; partial generation is never presented as complete;
the full SSE sequence is `progress → report → plan → done`.

Verified: a validated plan and its tasks are saved atomically with analysis completion. The 14-day label
defines the due-date window; it does not require 14 tasks.

### `P-03` — Add plan UI and task updates — Completed

Scope: Display the plan and implement status/due-date updates only.

Acceptance: refresh preserves changes; cross-user updates return `404`; completed timestamps follow
status changes consistently.

Verified: the UI displays persisted plans and current tasks, supports status and due-date changes, sets or
clears `completedAt` consistently, and reloads saved state after refresh.

### `P-04` — Add evidence-aware normalized task selection — Completed

Scope: Normalize report/JD gaps to canonical focus areas, enforce one task per canonical gap, require a
concrete deliverable, and select task types according to available evidence.

Verified:

- current plans contain at most eight tasks and never fill capacity by duplicating a canonical gap;
- supported task types are `RESUME_APPLICATION`, `INTERVIEW_STORY`, `EVIDENCE_VERIFICATION`, and
  `CONCEPT_LEARNING`;
- strong and partial evidence is reused before concept learning is considered;
- missing skills never become claimed experience;
- tools, incidents, root causes, metrics, and results absent from source evidence are rejected.

### `P-05` — Regenerate remaining tasks safely — Completed

Scope: Replace current `TODO` tasks while preserving protected progress and retaining replaced rows as
archived history.

Verified:

- regeneration archives and replaces current `TODO` tasks instead of appending to them;
- `IN_PROGRESS`, `COMPLETED`, and `SKIPPED` tasks remain current and protected;
- protected plus regenerated current tasks never exceed eight;
- archived rows are retained in the database but excluded from current-task reads and API responses.

### `P-06` — Complete analysis with a deterministic safe fallback — Completed

Scope: If generated plan JSON parses but fails semantic validation twice, create a deterministic safe plan
without persisting either rejected model output.

Verified:

- two bounded semantic failures invoke the fallback;
- fallback output is validated before persistence;
- it produces at most one evidence-verification task per normalized gap and at most eight current tasks;
- unsupported model tools, experiences, incidents, causes, metrics, and results are not persisted;
- default tests do not call a live model.

## 9. V1 acceptance

### `V1-01` — End-to-end acceptance, regression tests, and documentation sync — Completed

Scope: Verify the implemented V1 flow, add the smallest deterministic backend end-to-end coverage, and
synchronize the product, API, and data-model documents without changing production behavior.

Verified on 2026-09-01:

- one PostgreSQL-backed HTTP acceptance test covers registration/login, pasted Resume/JD creation and
  parsing, analysis creation, `report → plan → done` SSE replay, persisted refresh, task status/due-date
  update, and replace-style regeneration;
- the same test proves cross-user `404`, immutable report JSON across plan updates, max-eight current tasks,
  unique evidence-backed gaps, archived-task exclusion, rejected model-plan non-persistence, and successful
  deterministic fallback completion;
- parser, report, and plan model boundaries are deterministic fakes; no live AI call is made;
- the focused regression set passes 71/71, the PostgreSQL end-to-end acceptance test passes 1/1, and
  the default Maven suite passes 105/105;
- no production source, migration, dependency, public API, or frontend file changes are required.

## 10. Post-V1 input improvements

### `U-01` — PDF/DOCX Resume upload — Completed

Scope: Add a separate authenticated multipart endpoint and Resume-only browser control. Validate and extract
plain text from `.pdf` and `.docx` files, save only that text through the existing Resume repository, and reuse
the existing parse and Analysis flow. Preserve pasted-text creation and all report/plan behavior.

Implemented boundaries:

- `POST /api/resumes/upload` returns the existing Resume resource with `parseStatus = NOT_STARTED`;
- uploaded bytes are limited to 5 MiB and normalized extracted text to 100,000 characters;
- PDF extraction is limited to 50 pages and a 32 MiB in-memory parser cache; it does not create an
  original-file temporary copy;
- extension, declared MIME, magic bytes, actual PDF/OOXML structure, encryption, and macro content are checked;
- empty, damaged, encrypted, unsupported, oversized, and textless/scanned documents fail before persistence;
- Apache PDFBox 3.0.8 is used for position-aware PDF text extraction and Apache POI XWPF is used for DOCX
  text extraction; iText remains available for the application's existing PDF generation features;
- files, filenames, MIME types, and reconstructed formatting are not persisted, so no migration is required;
- extracted content remains untrusted model data and is not written to logs or reflected in unsafe errors;
- the JSON pasted-text API, ownership isolation, Analysis history, and P-05/P-06 behavior are unchanged.

Verified on 2026-09-01:

- synthetic in-memory extractor tests pass 5/5 and cover valid PDF/DOCX, position-separated WPS-style text,
  technical tokens, unsupported and mismatched types, empty, damaged, textless, macro-enabled, encrypted,
  oversized-file and oversized-text inputs, and the 50-page limit;
- PostgreSQL-backed upload integration tests pass 5/5 against an isolated temporary database, covering
  upload-to-parse reuse, ownership isolation, safe errors, and failure-before-insert behavior;
- a browser smoke test uploaded and parsed synthetic PDF and DOCX files and reused uploaded Resume 27 in
  completed Analyses 32 and 33; inspection then exposed iText word-boundary loss in a valid WPS-generated PDF,
  so PDF upload extraction was replaced with PDFBox instead of accepting a merely non-empty result;
- a non-persisting assertion against the external synthetic WPS PDF confirms the replacement preserves every
  expected word boundary without adding the file to the repository; observed logs contain no document text,
  credentials, or complete model response;
- the current default Maven suite passes 128/128 without a live AI call;
- the frontend production build passes, and `git diff --check` reports no whitespace errors.

## 11. Interview preparation

### `IP-01` — Evidence-grounded interview question set — Completed

Scope: Generate and persist one immutable interview-preparation question set from one owned completed
Analysis. Use only the validated match report and parsed Resume/JD evidence. Do not add a frontend,
answer capture, scoring, voice, RAG, or STAR-story generation.

Required boundaries:

- every Analysis has at most one interview session, and repeated creation returns the existing session;
- each session contains 5–8 ordered questions with a fixed type, question, assessment goal, exact source
  evidence, and preparation tip;
- supported types are `TECHNICAL_GAP`, `PROJECT_FOLLOW_UP`, and `BEHAVIORAL_EVIDENCE`;
- project and behavioral questions require concrete Resume project/work evidence; report gaps or JD
  requirements cannot be converted into claimed experience;
- model-facing evidence is represented by server-issued, type-scoped IDs (`G1...` for technical-gap
  evidence and `E1...` for project/work evidence); the server resolves a permitted ID back to the exact
  validated text before semantic validation and persistence, and IDs are never returned or stored;
- model JSON receives strict duplicate-field, schema, count, de-duplication, evidence, and no-invention
  validation before any row is written;
- two invalid outputs fail safely without partial persistence; default tests use a deterministic fake and
  never call a live model;
- IP-01 does not modify historical Analysis reports, plans, plan tasks, max-eight behavior, archiving,
  regeneration, or the skipped Q-01/Q-02 fields.

Verified on 2026-09-01:

- focused service and controller tests pass 17/17 with a deterministic fake and no live model call;
- PostgreSQL-backed integration and V7 schema tests pass 4/4 against an isolated temporary database;
- Flyway applies V1 through V7 cleanly, persists five ordered questions atomically, returns the same
  immutable session on repeated creation, and enforces cross-user `404` behavior;
- duplicate or unknown JSON fields, unknown or non-exact evidence, incomplete inputs, and two invalid
  generations are rejected without creating an interview session;
- a live-model smoke test for Analysis 31 created session 3 with eight evidence-grounded questions across
  all three required types; manual review found no invented experience, person, incident, tool, metric, or
  result, and a repeated request returned `200` with the unchanged session ID 3;
- the current default Maven suite passes 128/128, the frontend production build passes, and `git diff --check`
  reports no whitespace errors.

### `IP-02` — Interview preparation browser experience — Completed

Scope: Expose the immutable IP-01 question set inside the existing completed match-report dialog. Reuse the
idempotent creation endpoint and do not add answers, scoring, voice, RAG, backend fields, or database changes.

Implemented boundaries:

- only a completed Analysis with a report exposes the interview-preparation control;
- the browser calls `POST /api/analyses/{analysisId}/interview-prep`; `201` displays a new session and `200`
  displays the existing immutable session without regenerating it;
- all ordered questions display their fixed type, question, assessment goal, exact source evidence, and
  preparation tip;
- safe backend errors are shown without exposing response internals, and the control prevents duplicate clicks;
- closing or switching reports invalidates pending UI responses so an older request cannot populate another
  report, including the close-and-reopen-same-analysis case;
- a page reload does not need a new discovery endpoint: clicking the same control safely reloads an existing
  session through the idempotent POST.

Verified on 2026-09-01:

- no production backend, migration, public API, dependency, or data-model change is required;
- the frontend production build passes and `git diff --check` reports no whitespace errors;
- existing controller and integration coverage continues to verify first-create `201`, repeat `200`, ordered
  questions, authentication, ownership isolation, and safe error behavior.

## 12. Resume review

### `RR-01` — Synthetic retrieval-backed Resume Review API — Completed

Scope: Demonstrate the retrieval and evidence-safety boundary for Resume Review without committing or
processing proprietary guidance. Review one owned, successfully parsed Resume against a small public
synthetic rule corpus and return ephemeral structured suggestions. Do not enable vector RAG,
remote embeddings, pgvector, model generation, persistence, or a browser UI.

Implemented boundaries:

- `POST /api/resumes/{resumeId}/review` accepts no body and returns suggestions only for the current user's
  `COMPLETED` Resume;
- every suggestion includes a fixed category and priority, a verbatim value from the validated parsed Resume,
  a conditional non-inventive recommendation, and an opaque public synthetic source identifier;
- deterministic local lexical matching, stable ordering, and de-duplication produce at most 12 suggestions;
- the bundled rule resource is strictly validated with duplicate-field detection and explicit size, count,
  classification, identifier, term, and field-length limits;
- the result is not persisted and does not change Resume text, Analysis reports, preparation plans, interview
  sessions, or their histories;
- no vector store, embedding model, chat model, external service, new dependency, or database migration is used;
- `/careerpilot-private-knowledge/` is ignored by Git. RR-01 neither loads that directory nor contains paid or
  proprietary source material.

Verified on 2026-09-01:

- focused service and controller tests pass 7/7 without a live AI call;
- ownership/not-found behavior, incomplete parsing, verbatim evidence, deterministic ordering, de-duplication,
  strict corpus validation, and safe errors are covered;
- the current default Maven suite passes 135/135 without a live AI call;
- the frontend production build passes, and `git diff --check` reports no whitespace errors.

### `RR-02` — Resume Review browser experience — Completed

Scope: Expose the ephemeral RR-01 response inside the existing Resume detail dialog. Reuse the current API
and do not add persistence, automatic rewriting, private knowledge ingestion, model calls, vectors, or new
dependencies.

Implemented boundaries:

- only a Resume with `parseStatus = COMPLETED` displays the `Review resume` control;
- the browser calls `POST /api/resumes/{resumeId}/review` and renders suggestions in the server-provided order;
- each card shows category, priority, finding, verbatim Resume evidence, recommendation, and the public
  synthetic source label and identifier;
- zero suggestions is a valid empty state, while backend errors use the existing safe message envelope;
- the dialog states that the guidance is public and synthetic, is not vector AI/RAG, is not saved, and does
  not modify the Resume;
- opening, closing, switching documents, or signing out invalidates pending responses, including the
  close-and-reopen-same-Resume case;
- results remain dialog-local and are not attached to the Resume list or browser storage.

Verified on 2026-09-01:

- no backend, migration, dependency, or public API change is required;
- the frontend production build passes and `git diff --check` reports no whitespace errors;
- the existing RR-01 controller and service tests continue to cover authentication, ownership, lifecycle,
  deterministic suggestions, evidence, and safe errors.

### `RR-03` — Model-assisted Resume Review selection — Completed

Scope: After RR-01 lexical retrieval, let the configured chat model select the most relevant retrieved
candidate pairs. Preserve every public suggestion field as server-owned data and fall back to the complete
deterministic RR-01 result when model selection is unavailable or invalid. Do not add vector retrieval,
private material, persistence, or free-form model-generated advice.

Implemented boundaries:

- the model receives only lexically retrieved public synthetic candidates, each containing a server-issued
  `ruleId` and `evidenceId` plus its category, priority, public rule recommendation, and exact parsed Resume
  evidence; before the request, the server reduces the internal retrieval to at most six candidates and two per
  category; the model does not receive the raw Resume, complete parsed document, private path, or full corpus;
- candidate fields are untrusted data and the model may return only
  `{"suggestions":[{"ruleId":"...","evidenceId":"..."}]}`;
- strict duplicate-field and unknown-field rejection, bounded identifiers, a 1–6 count, exact offered-pair
  membership, pair de-duplication, and a maximum of two suggestions per category run before any selection is
  accepted;
- category, priority, finding, Resume evidence, recommendation, source identifier, and source title are
  resolved exclusively from the current server candidate; model prose can never enter the response;
- two structurally or semantically invalid outputs use the deterministic RR-01 candidate set; provider runtime
  failure falls back immediately; an empty retrieval result skips the model entirely;
- safe warnings contain only fixed rejection categories and attempt numbers, never prompts, candidate text,
  model output, provider messages, Resume content, source bodies, filenames, or paths;
- `reviewType` is `MODEL_ASSISTED_SYNTHETIC_LEXICAL_RULES` after a valid selection,
  `SYNTHETIC_LEXICAL_RULES_FALLBACK` after fallback, or `SYNTHETIC_LEXICAL_RULES` for an empty retrieval;
- RR-02 displays the model-assisted or fallback state and accurately describes this as lexical, not vector, RAG;
- the deterministic fallback applies the same six-suggestion and two-per-category limits, preventing a long list
  of repetitive skill suggestions when many technologies match the same guidance rule.

Verification completed on 2026-09-01:

- RR-03 focused backend tests: 9/9 passed;
- default Maven test suite: 137/137 passed, with fake generators and no live AI calls;
- frontend `npm run build`: passed;
- `git diff --check`: passed with no whitespace errors (Git emitted only existing LF/CRLF conversion warnings).
- authenticated browser smoke test: real model selection completed and returned the
  `MODEL_ASSISTED_SYNTHETIC_LEXICAL_RULES` path; the observed repetitive selection was used to add the final
  six-suggestion and category-diversity guardrails.
- post-guardrail authenticated browser smoke test on 2026-09-02: the real model-assisted path completed without
  fallback and returned three server-owned suggestions across Projects, Skills, and Education, with no repeated
  Skills advice beyond the configured category limit.

## 13. Local operations

### `OPS-01` — AI-optional local startup — Completed

Scope: Make the default local process start without a DashScope API key while keeping non-AI CareerPilot
features usable. Require an explicit opt-in for live model calls and reject AI-required writes before they
create or mutate lifecycle state.

Implemented boundaries:

- `CAREERPILOT_AI_ENABLED` defaults to `false`; `DASHSCOPE_API_KEY` may be absent in that mode;
- DashScope chat/embedding auto-configuration and every live CareerPilot AI component are conditional
  on the explicit AI flag; an early environment hook excludes every DashScope auto-configuration in offline
  mode because that library version does not consistently honor its per-model enabled flags; safe offline
  implementations keep service dependencies resolvable;
- Resume/JD CRUD, PDF/DOCX validation and text extraction, authentication, owned reads, completed reports,
  plans, tasks, and interview sessions remain available without AI;
- Resume/JD parsing returns `503 AI_UNAVAILABLE` after ownership is checked but before `RUNNING` is written;
- Analysis creation validates the completed owned inputs, then returns `503 AI_UNAVAILABLE` before a
  `PENDING` row is inserted;
- Interview preparation returns an existing owned session when one already exists; creating a new session
  offline returns `503 AI_UNAVAILABLE` before model invocation or persistence;
- Resume Review keeps its deterministic synthetic-guidance fallback, and plan regeneration keeps its existing
  deterministic evidence-based fallback;
- no database migration, secret persistence, or provider exception disclosure was added.

Verification completed on 2026-09-02:

- OPS-01 focused tests: 44/44 passed;
- default Maven test suite: 143/143 passed with no live AI call;
- frontend `npm run build`: passed;
- an actual `spring-boot:run` smoke with `DASHSCOPE_API_KEY` removed and AI disabled reached a successful
  Tomcat startup on random port `12581`, after which the temporary process was intentionally stopped;
- the offline Spring context started with all DashScope auto-configuration excluded;
- service tests proved parsing state, Analysis rows, and Interview Session rows are unchanged on offline
  rejection;
- controller coverage verified the safe `503 AI_UNAVAILABLE` envelope;
- `git diff --check`: passed with no whitespace errors (only existing LF/CRLF conversion warnings).

## 14. Portfolio packaging

### `PORT-01` — Startup guide, architecture, screenshots, and demo flow — Completed

Scope: Package the implemented CareerPilot flow for honest portfolio presentation without changing product
behavior, API contracts, persistence, dependencies, or deployment architecture.

Delivered boundaries:

- the root README now leads with a CareerPilot-only product summary, reliable PostgreSQL/backend/frontend
  startup, offline/live-AI behavior, and verification commands;
- `docs/portfolio-guide.md` contains the modular-monolith Mermaid architecture, engineering/safety talking
  points, current limitations, and a timed 3–5 minute demonstration script;
- six real browser screenshots cover sign-in, Resume/JD input including upload, match report, preparation plan,
  interview questions, and Resume Review;
- screenshots use an isolated PostgreSQL database, an `example.invalid` account, synthetic Resume/JD content,
  and AI-disabled deterministic behavior; the temporary database/container and capture helpers were removed;
- no personal Resume, proprietary knowledge source, provider key, Cookie/token, database password, server path,
  or full model response is included in the portfolio assets;
- the README contains only the CareerPilot product, operation, and verification guidance.

Verification completed on 2026-09-02:

- all six PNG assets were visually inspected for readable UI content and synthetic-only data;
- the screenshot run exercised the real Vue UI and Spring Boot API against all seven Flyway migrations without
  a live model call;
- the final backend test suite, frontend production build, link/file checks, and `git diff --check` are recorded
  in the completion report for this task.

## 15. Production demo packaging

### `DEPLOY-01` — CI, production configuration, and synthetic-only online demo — Deployable package completed

Scope: Prepare a provider-neutral, production-shaped portfolio deployment without exposing personal data,
accepting public mutations, requiring a live AI provider, or changing existing CareerPilot persistence and API
behavior outside an explicitly enabled demo profile.

Delivered boundaries:

- GitHub Actions runs Java 21 backend tests plus normal and demo-mode frontend builds on pushes and pull
  requests; CI contains no deployment credential and makes no live model call;
- multi-stage backend and frontend images run as non-root where supported, use reproducible dependency installs,
  and exclude local secrets, private knowledge, build output, and repository metadata from build contexts;
- a production Compose stack keeps PostgreSQL and Spring Boot private and exposes only the frontend Nginx port;
- production configuration requires process-provided database/JWT secrets, enables Flyway, disables API docs and
  detailed errors, defaults to Secure Cookies, and keeps AI disabled;
- demo mode creates only the fixed `example.invalid` synthetic fixture, publishes no password, and resets only
  that exact fixture on restart;
- the Vue build hides mutation controls, while both Nginx and a backend filter independently reject public
  writes outside a narrow non-persistent/read-existing allowlist;
- `docs/deployment-guide.md` documents HTTPS, secret, single-replica, health-check, privacy, validation, and
  rollback boundaries.

Verified on 2026-09-02 with synthetic data only:

- focused demo authentication/read-only tests: 4/4 passed;
- default Maven suite: 147/147 passed without a live AI call;
- normal and demo-mode frontend production builds passed;
- production Compose validation plus backend/frontend image builds passed;
- the isolated three-container runtime reached healthy status and returned 200 for the UI, health, demo login,
  Resume Review, and existing Interview Preparation flows;
- the runtime exposed one fixed synthetic Resume and rejected a Resume create request with
  `403 DEMO_READ_ONLY`.

The repository package is deployable to a Docker host. Provider publication remains an owner-authorized
operation and is recorded separately from the provider-neutral package.

### `DEPLOY-02` — Provider publication and production verification — Completed

Scope: Publish the DEPLOY-01 synthetic-only image set on the owner's existing portfolio host and document the
verified provider-specific topology without automating provider accounts, exposing secrets, enabling live AI,
or accepting visitor data.

Completed on 2026-09-11:

- published the read-only demo at `https://careerpilot.hermanlidev.com` using Cloudflare DNS-only resolution,
  BaoTa-managed Nginx/Let's Encrypt TLS, and an existing DigitalOcean Ubuntu host;
- bound the Docker frontend to `127.0.0.1:18080`; Spring Boot and PostgreSQL remain private on the Compose
  network, and the host-level proxy is the only caller of the frontend port;
- loaded prebuilt `linux/amd64` images on the 1-vCPU/2-GB host instead of building there and retained explicit
  memory limits for all three services;
- verified healthy frontend, backend, and database containers, HTTPS 200 health, HTTP-to-HTTPS redirect, HSTS,
  security headers, and an externally unreachable port 18080;
- verified the synthetic workspace, match report, persisted plan, five read-existing interview questions, and
  non-persistent Resume Review without a live model call;
- confirmed the demo's mutation controls are hidden and its write boundary remains `403 DEMO_READ_ONLY`;
- deployed a frontend-only correction without recreating backend/database services and documented the same
  narrow update and rollback path.

DEPLOY-02 changes no product API, schema, dependency, model behavior, or normal local workflow. Secrets remain
only in the server-owned `.env.production`, and the public environment contains no personal Resume or licensed
knowledge source.

## 16. CareerPilot-only repository cleanup

### `LEGACY-01A` — RAG reference and exact cleanup inventory — Completed

Scope: Preserve the architectural knowledge needed for a possible future CareerPilot RAG feature and create an
evidence-based removal sequence before deleting obsolete non-CareerPilot functionality.

Completed on 2026-09-11:

- traced the obsolete RAG, agent, tool, MCP, backend route, frontend route, resource, test, and dependency graph;
- confirmed CareerPilot Resume Review uses its own public synthetic rule corpus and bounded lexical retrieval,
  with no import or runtime dependency on removed RAG code or knowledge documents;
- documented a future CareerPilot RAG pipeline covering authorized-source governance, bounded ingestion,
  deterministic chunking, metadata, embedding compatibility, scoped retrieval, citations, prompt-injection
  resistance, validation, fallback, privacy, deletion, re-indexing, and synthetic evaluation;
- defined and completed an exact keep set plus phased backend, resource, frontend, dependency, branding, and
  English-only cleanup inventory;
- recorded that the existing public deployment is unchanged until a separately verified image rollout; and
- made no Java, Vue, API, database, migration, dependency, Docker, or runtime change in this phase.

Next implementation phases:

- `LEGACY-01B`: completed removal of isolated backend code, resources, tests, and the standalone MCP module;
- `LEGACY-01C`: completed removal of the isolated retired Vue route, API wrapper, views, components, and starter
  assets;
- `DEPENDENCY-01`: completed removal of dependencies and configuration made unused by those deletions;
- `BRAND-01`: completed the Maven, Java package, application, and frontend identity migration to CareerPilot; and
- `EN-01`: completed the English-only repository and old-identifier sweep.

### `LEGACY-01B` — Obsolete backend code and resource removal — Completed

Completed on 2026-09-11:

- removed 35 isolated production Java files covering the old chat application, agent framework, RAG examples,
  invocation demos, unrestricted tools, file chat memory, and their shared helpers;
- removed the three old knowledge documents, MCP client descriptor, 12 corresponding tests, and the standalone
  image-search MCP module;
- removed obsolete Ollama, MCP, vector-store, search API, embedding enablement, and RAG-initialization
  configuration while retaining all CareerPilot settings;
- removed the retired routes from the current API contract and aligned production deployment documentation;
- preserved every CareerPilot controller, package, migration, test, synthetic rule, and read-only demo class;
- changed no Maven dependency, database schema, Flyway migration, CareerPilot endpoint, persisted data, frontend
  behavior, Docker service, or running public deployment;
- focused AI-optional startup and API tests passed 6/6;
- a clean default Maven build compiled 95 production and 38 test source files, then passed 144/144 tests without
  a live AI call; and
- the clean output and source scans contained none of the removed classes or resources.

### `LEGACY-01C` — Retired frontend removal — Completed

Completed on 2026-09-11:

- removed two retired views, four unused components, and the unused Vue and Vite starter assets;
- removed the retired route and chat-specific SSE wrapper while retaining the two CareerPilot routes, all
  authenticated CareerPilot API calls, and the Analysis event stream;
- replaced the remaining frontend document metadata and README with CareerPilot-only English content;
- changed no backend source, API contract, database schema, Flyway migration, dependency, Docker service, or
  running public deployment;
- normal and read-only demo Vite builds both passed with 99 modules transformed; and
- source and generated-output scans contained none of the removed route, API, component, starter, brand, or
  non-English or obsolete identifiers.

### `DEPENDENCY-01` — Unused dependency removal — Completed

Completed on 2026-09-11:

- reviewed source imports, the resolved Maven tree, the packaged JAR, and `mvn dependency:analyze` instead of
  applying the plugin's starter/autoconfiguration false positives blindly;
- removed 15 obsolete or redundant direct Maven dependencies covering the old model demos, RAG/vector/MCP
  examples, tools, serialization, API UI, PDF generation, JSON Schema override, and Lombok;
- removed the corresponding Lombok build configuration, retired Springdoc/Knife4j properties, and three
  obsolete milestone/snapshot repositories while preserving resolution from Maven Central;
- replaced the two iText-based synthetic PDF fixtures with the already-required PDFBox implementation;
- removed frontend `@vueuse/head`, its initialization, and its lockfile graph;
- retained the CareerPilot model starter, Web/validation/JWT, JDBC/PostgreSQL/Flyway, PDFBox/POI, and test stack;
- documented that generic RAG/vector classes remain only as an unavoidable transitive part of the retained
  Spring AI Alibaba starter and do not enable a CareerPilot vector store or RAG feature;
- focused Resume upload extraction tests passed 5/5, the complete default suite passed 144/144 without a live
  provider, and a fresh backend package produced a 62.4 MiB executable JAR with no obsolete class or removed
  direct-dependency name;
- clean-lockfile normal and read-only demo frontend builds both passed with 87 modules transformed, as did the
  restored local dependency installation and both local builds; and
- production Compose validation passed. No database schema, Flyway migration, CareerPilot product endpoint,
  persisted record, Docker service, or running public deployment changed.

### `BRAND-01` — CareerPilot package and artifact identity — Completed

Completed the identity-only migration without changing HTTP paths, database schema, Flyway migrations, or
CareerPilot behavior:

- Maven coordinates are now `com.hermanli:careerpilot`, and the packaged application is
  `careerpilot-0.0.1-SNAPSHOT.jar`;
- all 95 production and 38 test Java sources now live under `com.hermanli.careerpilot`;
- the Spring Boot entry point and context test are now `CareerPilotApplication` and
  `CareerPilotApplicationTests`;
- `spring.application.name`, Spring environment-post-processor metadata, Docker artifact paths, CI paths,
  Compose build context, and frontend package metadata now use CareerPilot identifiers;
- the frontend directory is now `careerpilot-frontend`;
- the root README and portfolio guide now present CareerPilot directly, without retired project material; and
- the default Maven suite passed 144/144 tests, both normal and read-only-demo frontend builds passed with 87
  modules transformed, the renamed JAR was inspected successfully, production Compose configuration validated,
  and `git diff --check` passed.

The repository checkout directory itself remains unchanged to avoid moving the active workspace. The running
public deployment was not changed.

### `EN-01` — English-only repository sweep — Completed

Completed on 2026-09-12:

- translated the final non-English source comment in the cross-origin configuration;
- replaced obsolete named UI, route, streaming, RAG, and origin references in the backlog, test scenarios, and
  future RAG design with CareerPilot-specific English descriptions;
- updated the Stage 0 scenarios to reflect current AI-optional startup and safe `503 AI_UNAVAILABLE` behavior;
- deleted the temporary cleanup inventory after its migration phases were completed;
- repository-content and filename scans found no CJK text, retired product/package identifiers, source-origin
  wording, or references to the deleted inventory;
- the complete default Maven suite passed 144/144 tests without a live model call;
- normal and read-only-demo frontend builds both passed with 87 modules transformed; and
- production Compose validation and `git diff --check` passed.

No HTTP path, database schema, Flyway migration, CareerPilot behavior, persisted data, running local service, or
public deployment changed. Licensing remains a separate owner decision.

### `RAG-01` — Public-synthetic Resume Review vector RAG — Completed

Scope: Extend the existing ephemeral Resume Review with a real, opt-in vector path while retaining its lexical
and deterministic fallbacks. Index only the bundled public synthetic Markdown guide; do not ingest private
licensed material or change Resume, Analysis, plan, interview, archiving, regeneration, or max-eight behavior.

Implemented boundaries:

- stable section chunks carry source/version/section/chunk/hash/visibility/index metadata and fixed UUIDs;
- Flyway V8 owns a 1024-dimensional pgvector table with cosine HNSW and metadata indexes;
- DashScope `text-embedding-v3`, Spring AI `PgVectorStore`, Top K 8, threshold 0.50, source/version filters,
  hash de-duplication, and a 6000-character context cap form the opt-in local path;
- index replacement is transactional; unavailable indexing, embedding, retrieval, or model selection returns to
  the existing lexical or deterministic path without partial Review persistence;
- the model selects only offered chunk/evidence pairs; exact Resume evidence, recommendations, and bounded
  citations are validated/resolved by the server;
- AI and RAG remain disabled in the hosted read-only demo, and `careerpilot-private-knowledge/` remains outside
  Git and Docker and is never loaded.

Verification completed on 2026-09-15, with live retrieval calibration completed on 2026-09-19:

- the default offline Maven suite passed 155/155 tests;
- an isolated pgvector container passed 4/4 external migration and real add/filter/search/delete tests using a
  deterministic stub embedding model;
- live DashScope verification confirmed all four knowledge categories and citations at threshold `0.50`; three
  combined-Resume runs entered vector RAG, while `0.60` and `0.65` safely fell back to lexical review;
- normal and read-only-demo frontend builds passed with 92 modules transformed;
- both Compose configurations and `git diff --check` passed.

### `PUBLIC-AUTH-01` — Google-only Clerk login and Spring Boot JWT verification — Implemented and locally accepted

Scope: Add a second, bounded authentication chain for a future public Resume Review without replacing the
existing CareerPilot account/session system or opening uploads and model calls in this task.

Implemented boundaries:

- `@clerk/vue` is loaded only when an explicit frontend feature flag is enabled; the sign-in panel obtains a
  session token without storing it in browser storage or a query string;
- `GET /api/rag/session` requires a Bearer token and verifies RS256/JWKS signature, issuer, timestamps, non-blank
  subject, exact authorized-party origin, and non-pending session status;
- the endpoint returns only `authenticated: true`; it does not disclose or persist Clerk identity fields;
- the existing CareerPilot email/password flow, HS256 Cookie, users, ownership rules, Resume data, and API
  behavior remain unchanged;
- the feature defaults off and performs no Resume upload, model call, vector retrieval, quota mutation, or
  database migration;
- Google-only sign-in is an explicit Clerk Dashboard requirement and is not claimed as code-enforced provider
  configuration.

Live acceptance completed on 2026-09-25:

- an owner-controlled Clerk development instance exposed Google as the sign-in path used by the application;
- a real browser session obtained a Clerk JWT that the backend accepted for `GET /api/rag/session` with the exact
  development issuer and `http://localhost:3000` authorized party;
- an anonymous request returned the expected safe `401`, while the authenticated UI displayed the verified
  session state without exposing the token or Clerk subject.

Remaining production acceptance: configure the exact production origin and publishable key, confirm the
production Clerk instance remains Google-only, and repeat the browser smoke test after deployment. The guard
still needs attachment to the future live RAG endpoint before any provider-backed rollout.

### `PUBLIC-AUTH-02` — Application-level Clerk identity and personal API authentication — Implemented and locally accepted

Scope: Make Google-only Clerk authentication the optional identity boundary for the whole personal CareerPilot
application while preserving the established `app_user.id BIGINT` ownership model.

Implemented boundaries:

- Flyway V10 adds a unique, complete `(clerk_issuer, clerk_subject)` mapping to `app_user`; Clerk-only users do
  not require fabricated email or password values;
- the backend atomically resolves or creates that mapping and puts only the resulting internal user ID into the
  existing request identity attribute used by all ownership-aware controllers and repositories;
- when `CAREERPILOT_CLERK_AUTH_ENABLED=true`, all personal Resume, job-description, Analysis, plan, interview,
  user, and RAG APIs require a valid Clerk Bearer JWT and no longer accept the legacy session Cookie;
- legacy password registration/login, demo-login, and Cookie logout endpoints return `404` in that mode, so
  Google is the only reachable account entry path;
- invalid, expired, wrong-issuer, wrong-party, pending, or malformed tokens return the same safe `401`, and
  browser-supplied email or user ID is never an authorization input;
- the frontend renders a Clerk gate at `/`, moves the personal workspace to `/app`, obtains a fresh token for
  Axios and SSE/fetch requests, and retains the prior feature-scoped panel only as a compatibility mode;
- Google-only provider availability remains a Clerk Dashboard requirement; no Clerk secret key is used by the
  application or exposed through Vite.

Live acceptance completed on 2026-09-25:

- the isolated `careerpilot-github-postgres` pgvector database applied Flyway V9 and V10 and reached schema
  version 10 without touching the older workspace database or volume;
- anonymous `GET /api/users/me` returned `401`, legacy `POST /api/auth/login` returned `404`, and a real Clerk
  session entered the private `/app` workspace;
- the first Google account saved two private Resume records, while a second Google account received a distinct
  internal user with an empty workspace and could not see either record;
- returning to the first account restored only its own records, confirming that all ownership queries continued
  to use the server-derived internal `app_user.id`.

Remaining production acceptance: repeat the anonymous, legacy-login, authenticated workspace, and two-account
isolation checks after deployment. Keep live model rollout disabled until the guard and live security acceptance
below are complete.

### `PUBLIC-UPLOAD-01` — Clerk-protected request-scoped Resume validation — Implemented and locally accepted

Scope: Let a Google-authenticated public-demo visitor submit one real Resume through the bounded PDF/DOCX
extractor without persisting private data or invoking an external model.

Implemented boundaries:

- `POST /api/rag/resume/validate` exists only when both public-auth and public-upload backend flags are true and
  requires a valid Clerk Bearer token through the existing public-RAG authentication interceptor;
- the existing extractor enforces PDF/DOCX type agreement, file signature/container validation, encryption and
  macro rejection, a 5 MiB byte limit, a 100,000-character text limit, and safe textless/scanned-PDF errors;
- original bytes and extracted text remain request-scoped; no Resume row, file, filename, text, or Clerk identity
  is persisted, logged, or returned;
- the response contains only acceptance, document type, and extracted-character count and performs no model,
  embedding, vector retrieval, or database mutation;
- the browser upload form is separately build-gated, obtains a fresh Clerk token per request, and appears only
  after backend session verification; backend and Nginx preserve an exact-path allowlist;
- all upload flags default to false, so the current hosted demo behavior is unchanged until an explicit rollout.

Verification completed on 2026-09-20:

- focused authentication/upload/extractor/filter tests passed 15/15 and the full deterministic backend suite
  passed 172/172 without a database container or live Clerk/model call;
- normal, read-only-demo, and auth-plus-upload frontend builds passed; the production frontend Docker image
  built successfully and its rendered Nginx configuration passed `nginx -t`;
- production Compose interpolation, `git diff --check`, and production-dependency audit passed with no known
  production npm vulnerability.

Live acceptance completed on 2026-09-25:

- a real Google-authenticated browser session submitted a text-based PDF through the bounded validation route;
- the response reported `PDF accepted` and 2,377 extracted characters, then cleared the file input;
- the validation result did not display or return Resume text, did not add a Resume to the demo document library,
  and accurately stated that the extracted content was discarded without an AI or RAG call.

Remaining production acceptance: repeat the bounded PDF/DOCX smoke test after deployment. Do not enable AI/RAG
for public users until the implemented guard is attached around the live provider call and its safe fallback
behavior is verified.

### `PUBLIC-RAG-GUARD-01` — Bounded public model-call guard — Implemented, live attachment pending

Scope: Establish cost and abuse ceilings before adding a public provider call, without counting temporary
Resume validation as model usage or exposing a live RAG endpoint in this task.

Implemented boundaries:

- Flyway V9 adds one daily counter table; its user key is a date-bound HMAC-SHA256 of the verified Clerk subject,
  and no raw subject, email, IP, Resume, prompt, context, or response is persisted;
- PostgreSQL locks the global counter before the user counter and atomically reserves both, enforcing defaults
  of three calls per user and 100 globally per UTC day even under concurrent requests;
- one fair Java semaphore allows at most two concurrent model-call permits and rejects excess work immediately
  without an unbounded queue;
- a conservative JDK-only estimator counts UTF-8 bytes as input-budget units and caps the combined prompt at
  6000 units, requested output at 800 tokens, and total budget at 6800 before quota reservation;
- the Nginx upload and reserved live-review exact paths share a trusted-client-IP bucket of two requests per
  minute with one immediate burst request and a safe JSON 429 response;
- guard startup requires public Clerk authentication and a separate HMAC secret of at least 32 bytes; all guard
  settings default off in local and production examples;
- no dependency, live endpoint, provider call, model key, frontend model control, or existing API behavior is
  introduced by this slice.

Verification completed on 2026-09-20:

- focused guard configuration/service/context tests passed 10/10, and the full deterministic backend suite
  passed 182/182 without a live Clerk or model call;
- an isolated, volume-free PostgreSQL 16 + pgvector container applied Flyway V1 through V9 and passed 3/3
  external quota tests, including 12 concurrent attempts capped at three and exact reserved-token counters;
- the production frontend image built successfully, its rendered Nginx configuration passed `nginx -t`, and an
  actual loopback proxy test returned `502`, `502`, then the expected safe JSON `429` for one client bucket while
  a different trusted client IP remained in a separate bucket;
- production Compose interpolation and the normal frontend production build passed. The temporary database and
  Nginx containers were stopped and removed without creating or deleting any database volume.

Remaining acceptance: the future live public RAG endpoint must obtain the verified Clerk subject, include its
entire prompt in the token estimate, acquire a permit immediately before DashScope, set the provider output cap
to the reserved value, close the permit in all paths, and safely fall back for every guard denial. Production
rollout also requires confirming that BaoTa overwrites `X-Real-IP` and that the frontend container remains bound
to host loopback.

### `PUBLIC-RAG-SECURITY-01` — Pre-model security and privacy hardening — Implemented, live model security acceptance pending

Scope: Harden the already implemented public upload, Clerk authentication, rate-limit, logging, and privacy
boundaries without adding or enabling a live provider-backed endpoint.

Implemented boundaries:

- DOCX pre-validation now rejects more than 1,000 archive entries, any expanded entry above 10 MiB, more than
  20 MiB total expanded content, unknown expanded sizes, and absolute or parent-traversing archive paths before
  document parsing; the existing 5 MiB upload, 50-page PDF, 100,000-character text, encryption, macro, signature,
  and POI ZIP-bomb protections remain active;
- ambiguous, duplicate, malformed, and whitespace-containing Authorization headers are rejected before JWT
  decoding, while all token validation failures retain one non-disclosing `401` contract;
- every authenticated public-RAG route receives a generated request ID plus `no-store`/`no-cache` response
  headers; its audit event contains only request ID, method, fixed path, status, and latency;
- Spring request-detail logging stays explicitly disabled, and automated canary checks verify that token,
  decoder, filename, identity, and Resume contents do not enter the response or audit message;
- Nginx keeps an exact-path shared bucket at two requests per trusted IP per minute with one immediate burst,
  validates IPv4/IPv6-shaped trusted headers more narrowly, and adds bounded client/proxy inactivity timeouts;
- the validation UI and operations guide state that CareerPilot does not store the upload and that validation
  does not contact AI. They require a separate, provider-accurate processing disclosure before live review.

Verification completed on 2026-09-20:

- the focused upload/authentication/audit/Nginx security set passed 16/16, including compressed DOCX expansion,
  archive traversal, ambiguous Authorization headers, fixed-route audit redaction, and no-store response checks;
- the full deterministic backend suite passed 185/185 without a live Clerk or model call, and the frontend
  production build passed with 127 modules transformed;
- the optional-auth production frontend image built successfully and its rendered configuration passed
  `nginx -t`; loopback requests produced `502`, `502`, then safe JSON `429` for one trusted IP, while a second
  valid IP remained independent and changing invalid IP headers shared the fallback bucket (`502`, `429`);
- production Compose interpolation and `git diff --check` passed. The exact temporary Nginx container and image
  were removed, and no database container or volume was created, changed, or deleted for this task.

Live model security acceptance pending: prompt-injection isolation for uploaded Resume chunks, full-prompt budget
coverage, in-memory embedding disposal, provider timeout/failure fallback, structured output validation, and
end-to-end provider data-handling verification cannot be accepted until the live endpoint exists. All related
feature flags remain default-off.

## 17. Deferred after DEPLOY-02

- legacy `.doc`, image uploads, OCR, scanned-PDF text recognition, original-file storage, and Resume download;
- optional follow-up questions only when a future workflow has a concrete missing decision that changes output;
- interview answers, completion state, scoring, voice simulation, and interview knowledge-base RAG;
- private licensed knowledge ingestion, unrestricted/local-private vector retrieval, free-form
  generated Resume advice, and Resume Review persistence;
- job-board integrations or scraping;
- notifications, calendars, Redis, queues, microservices, and provider-specific cloud provisioning;
- persisted general chat history;
- autonomous agents, MCP, shell, downloads, or filesystem tools.

Each deferred feature requires a new problem statement and acceptance criteria before implementation.
