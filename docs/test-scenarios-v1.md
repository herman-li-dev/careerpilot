# CareerPilot V1 Test Scenarios

Status: Test design; scenarios become executable with their corresponding delivery stage.

## 1. Test strategy

- Use synthetic English resumes and job descriptions. Never commit real personal data.
- Use a deterministic fake or mock LLM for automated parsing, report, retry, timeout, and malformed-output tests.
- Keep one manually triggered live-provider smoke test outside the default test suite.
- Mark tests that use a live provider, database, terminal, network, or filesystem side effect with the JUnit `external` tag.
- `./mvnw test` must remain safe by default. The `external-tests` Maven profile is an explicit opt-in and is never used in routine verification.
- Test user-visible behavior and ownership boundaries before implementation details.
- Add tests with each stage; do not defer all automation to the final deployment stage.

## 2. Stage 0 baseline

| ID | Scenario | Expected result |
|---|---|---|
| `B-001` | Start the backend with Java 21 and AI disabled | Application reaches the ready state on port `8123` without a model key |
| `B-002` | Request `GET /api/health` | Returns `200` and `ok` |
| `B-003` | Request an AI-required operation while AI is disabled | Returns `503 AI_UNAVAILABLE` without creating partial lifecycle state |
| `B-004` | Run the explicit live-provider smoke test with temporary valid configuration | The bounded CareerPilot flow completes without logging source text or credentials |

Stage 0 is a baseline only. It does not prove CareerPilot business behavior.

## 3. Authentication and isolation

| ID | Scenario | Expected result |
|---|---|---|
| `A-001` | Register with a new valid email and password | User is created; password is stored only as a hash |
| `A-002` | Register the same normalized email twice | Second request returns a stable conflict/validation error |
| `A-003` | Login with correct credentials | Authentication cookie is set and current-user data is returned |
| `A-004` | Login with a wrong password | Returns `401` without revealing which credential was wrong |
| `A-005` | Access a protected endpoint without a session | Returns `401` |
| `A-006` | User A requests User B's resume/report/plan ID | Returns `404`; no fields from User B are exposed |
| `A-007` | User A attempts to update User B's plan task | Update is rejected and the task remains unchanged |

## 4. Resume and job-description management

| ID | Scenario | Expected result |
|---|---|---|
| `D-001` | Save valid English resume text | Original text is persisted before parsing and remains available under the current user |
| `D-002` | Save empty or whitespace-only resume text | Returns field validation error; no record is created |
| `D-003` | Save valid English job-description text | Original text is persisted under the current user |
| `D-004` | Parser returns valid structured JSON | Parsed JSON is saved and status becomes `COMPLETED` |
| `D-005` | Parser returns malformed or incomplete JSON | Status becomes `FAILED`, original text remains, and retry is possible |
| `D-006` | Retry a failed parse through the owned parse endpoint | One owned record is updated; no duplicate half-record is created |
| `D-007` | Start analysis while either input is not parsed successfully | Returns `409`; no analysis work starts |

PDF/DOCX boundary tests are deliberately deferred until file upload is implemented.

## 5. Match-report flow

Use a fixed synthetic resume/JD pair with known overlapping and missing skills.

| ID | Scenario | Expected result |
|---|---|---|
| `R-001` | Start analysis with two owned, valid inputs | Analysis is persisted as `PENDING` before model execution |
| `R-002` | Fake model returns a valid report | Status becomes `COMPLETED`; all fixed fields are stored |
| `R-003` | Model returns score below 0 or above 100 | Invalid output is not silently stored |
| `R-004` | Model omits a required list | Bounded retry occurs; persistent failure becomes `MODEL_OUTPUT_INVALID` |
| `R-005` | Model returns malformed JSON twice | Analysis becomes `FAILED`; API remains available |
| `R-006` | Provider times out | Analysis becomes `FAILED` with safe retryable error; no stack trace or key is returned |
| `R-007` | Resume lacks evidence for a required JD skill | Skill appears as missing; report does not invent experience |
| `R-008` | Retrieve report history | Only the current user's reports are returned in stable order |
| `R-009` | Re-run the same resume/JD pair | A new historical analysis is created; previous report is unchanged |
| `R-010` | Restart after an analysis was persisted but before model work completed | Abandoned work becomes safely retryable `FAILED`; it does not remain misleadingly `RUNNING` |

## 6. Follow-up questions and SSE

| ID | Scenario | Expected result |
|---|---|---|
| `S-001` | Required target location is missing | Analysis becomes `WAITING_FOR_USER_INPUT`; a `question` event is emitted and the stream closes |
| `S-002` | Submit the matching required answer | Answer is saved and analysis can continue |
| `S-003` | Submit an answer for the wrong question or state | Returns `409`; analysis data is unchanged |
| `S-004` | Successful stream | Events follow a valid order and end with exactly one `done` event |
| `S-005` | Model failure during stream | `error` is emitted, stream closes, and persisted state is `FAILED` |
| `S-006` | Disconnect and reconnect | Current persisted status/report is recoverable; no duplicate report is created |
| `S-007` | User A opens User B's event URL | Returns `404` before any event data is sent |

Valid event sequences include:

```text
progress → report → done
progress → report → plan → done
progress → question
progress → error
```

The first sequence is the report-only milestone. The second becomes the full V1 sequence after plan generation is implemented.

## 7. Career plan and tasks

| ID | Scenario | Expected result |
|---|---|---|
| `P-001` | Generate a plan from a completed report | One 14-day plan and its tasks are persisted atomically |
| `P-002` | Inspect generated tasks | Every task contains English title, description, due date, priority, status, and source evidence |
| `P-003` | Mark a task complete | Status and `completedAt` are saved and remain after refresh |
| `P-004` | Move a task due date | Only the owned task's due date changes |
| `P-005` | Plan generation fails halfway | No partial task list is presented as a completed plan |

## 8. Security and privacy checks

| ID | Scenario | Expected result |
|---|---|---|
| `SEC-001` | Search committed files and test output for known fake-secret patterns | No real provider key, password, token, or identity document is present |
| `SEC-002` | Provider returns a detailed error containing request metadata | Client receives a safe error without credentials or raw stack trace |
| `SEC-003` | Resume contains instruction-like text aimed at the model | Content is treated as resume data and cannot invoke tools or override system behavior |
| `SEC-004` | Analysis prompt requests shell/download/filesystem action | No such application-facing tool is available |
| `SEC-005` | Delete a resume referenced by a report | Historical integrity rule is enforced; no silent cascade removes the report |
| `SEC-006` | Send a state-changing cookie-authenticated request from a disallowed origin | Request is rejected before business data changes |
| `SEC-007` | Submit malformed, macro-enabled, encrypted, path-traversing, or expansion-limit PDF/DOCX content | Request fails with a stable safe error and no document content is returned or persisted |
| `SEC-008` | Send missing, duplicate, non-Bearer, whitespace-containing, expired, wrong-origin, or invalid-signature Clerk credentials | Request returns the same safe `401` and never trusts browser identity fields |
| `SEC-009` | Inspect public-RAG response headers and audit events using token/document canaries | Response is `no-store`; logs contain request metadata only and no canary, identity, header, filename, or body |
| `SEC-010` | Send three immediate requests to either exact public-RAG Nginx path from one trusted IP | The third request returns safe JSON `429`; a separate trusted IP uses a separate bucket |
| `SEC-011` | Read the validation privacy notice | It says CareerPilot does not store the upload and that validation does not contact AI; live-provider disclosure remains pending |
| `SEC-012` | In application-auth mode, call personal APIs with no token, a legacy Cookie, user A's Clerk token, and user B's resource ID | No token or Cookie returns `401`; valid tokens map to distinct internal users; cross-user access returns `404` without exposing ownership |

## 9. Milestone release gates

### M1: Application foundation

- Stage 0 baseline passes.
- Authentication and cross-user isolation tests pass.
- Database migrations run on a clean PostgreSQL instance.

### M2: Matching loop

- Resume/JD persistence and parsing failure tests pass.
- Structured report tests pass with a mock LLM.
- One manual live-provider smoke test succeeds without logging the key.

### M3: Questions and plan

- SSE ordering, reconnect, ownership, and terminal-state tests pass.
- Plan persistence and task-update tests pass.

### M4/M5: RAG and delivery

- Resume Review RAG uses only the fixed public synthetic source and verifies stable chunks/metadata, source
  filtering, the calibrated `0.50` default threshold, de-duplication, context bounds, citations, injection
  resistance, and safe fallback.
- The default suite uses fake vector/model dependencies; the external profile verifies Flyway V8 and actual
  pgvector add/filter/search behavior with deterministic stub embeddings in an isolated database.
- PUBLIC-RAG-GUARD-01 unit tests cover property validation, HMAC-only identity storage, conservative token
  rejection, immediate concurrency rejection, idempotent permit release, and quota-denial release. Its external
  PostgreSQL test verifies Flyway V9 plus concurrent user and cross-user global reservation limits.
- PUBLIC-RAG-SECURITY-01 pre-model tests cover container expansion/path limits, authorization-header ambiguity,
  response/log redaction, no-store headers, exact Nginx rate-limit paths, and accurate validation privacy text.
  PUBLIC-RAG-LIVE-01 tests additionally cover ownership-before-quota, unguarded-route removal, quota/busy errors,
  permit release on exception, explicit output-token options, provider failure, and invalid-output fallback.
- A future interview-knowledge RAG requires its own fixed question set and acceptance criteria.
- Backend tests, frontend build/E2E, Docker Compose startup, and README clone-to-run steps pass before final release.

## 10. Test evidence to record

For each milestone, record:

- command and environment used;
- pass/fail counts;
- any skipped external-provider test and reason;
- defects found and their resolution;
- the exact synthetic fixture version used.

Passing a live chat once is not a substitute for deterministic automated coverage.
