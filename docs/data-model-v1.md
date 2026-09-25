# CareerPilot V1 Data Model

Status: Implemented through RR-03 and Flyway V7 on 2026-09-01.

## 1. Design principles

- Use one PostgreSQL database for the monolithic application.
- Use `BIGINT` generated primary keys and UTC timestamps.
- Name the user table `app_user` because `user` is a PostgreSQL keyword.
- Keep original resume/JD text separate from model-produced JSON.
- Treat model output as untrusted until it passes validation.
- Persist task status before and after model calls so failures are visible and retryable.
- Enforce ownership in every user-facing query. A supplied record ID is never sufficient authorization.
- Do not add Redis, a message queue, event sourcing, or a separate vector database for V1; the bounded public
  synthetic review index uses the existing PostgreSQL database with pgvector.

## 2. Relationship overview

```text
AppUser
├─ UserProfile
├─ Resume
├─ JobDescription
└─ AnalysisReport
   ├─ CareerPlan
   │  └─ PlanTask
   └─ InterviewSession
      └─ InterviewQuestion
```

`AnalysisReport` references the exact resume and job description used for the analysis. Historical reports remain reproducible even if the user later creates newer inputs.

## 3. Tables

### `app_user`

| Column | Type | Constraints / purpose |
|---|---|---|
| `id` | `BIGINT` | Primary key, generated |
| `email` | `VARCHAR(320)` | Required, unique, normalized for lookup |
| `password_hash` | `VARCHAR(255)` | Required; never store a plain password |
| `status` | `VARCHAR(20)` | `ACTIVE` or `DISABLED` |
| `created_at` | `TIMESTAMPTZ` | Required |
| `updated_at` | `TIMESTAMPTZ` | Required |

### `user_profile`

| Column | Type | Constraints / purpose |
|---|---|---|
| `user_id` | `BIGINT` | Primary key and FK to `app_user.id` |
| `target_role` | `VARCHAR(120)` | Optional until analysis requires it |
| `target_location` | `VARCHAR(120)` | City/province or `Remote` |
| `work_authorization` | `VARCHAR(160)` | User-provided summary; do not infer |
| `weekly_hours` | `SMALLINT` | Optional, positive, reasonable upper bound |
| `education_summary` | `TEXT` | Optional |
| `created_at` | `TIMESTAMPTZ` | Required |
| `updated_at` | `TIMESTAMPTZ` | Required |

### `resume`

| Column | Type | Constraints / purpose |
|---|---|---|
| `id` | `BIGINT` | Primary key, generated |
| `user_id` | `BIGINT` | Required FK to `app_user.id` |
| `title` | `VARCHAR(160)` | Required user-facing label |
| `raw_text` | `TEXT` | Required English text: pasted input or normalized text extracted from a PDF/DOCX upload |
| `parsed_json` | `JSONB` | Nullable until validated parsing succeeds |
| `parse_status` | `VARCHAR(30)` | `NOT_STARTED`, `RUNNING`, `COMPLETED`, or `FAILED` |
| `parse_error` | `VARCHAR(500)` | Nullable safe error summary; no raw secrets |
| `created_at` | `TIMESTAMPTZ` | Required |
| `updated_at` | `TIMESTAMPTZ` | Required |

### `job_description`

| Column | Type | Constraints / purpose |
|---|---|---|
| `id` | `BIGINT` | Primary key, generated |
| `user_id` | `BIGINT` | Required FK to `app_user.id` |
| `title` | `VARCHAR(160)` | Required user-facing label |
| `company_name` | `VARCHAR(160)` | Optional; user or validated parser value |
| `role_title` | `VARCHAR(160)` | Optional; user or validated parser value |
| `raw_text` | `TEXT` | Required original English text |
| `parsed_json` | `JSONB` | Nullable until validated parsing succeeds |
| `parse_status` | `VARCHAR(30)` | Same values as `resume.parse_status` |
| `parse_error` | `VARCHAR(500)` | Nullable safe error summary |
| `created_at` | `TIMESTAMPTZ` | Required |
| `updated_at` | `TIMESTAMPTZ` | Required |

### `analysis_report`

| Column | Type | Constraints / purpose |
|---|---|---|
| `id` | `BIGINT` | Primary key, generated |
| `user_id` | `BIGINT` | Required FK to `app_user.id` |
| `resume_id` | `BIGINT` | Required FK to `resume.id` |
| `job_description_id` | `BIGINT` | Required FK to `job_description.id` |
| `status` | `VARCHAR(30)` | Analysis lifecycle state |
| `match_score` | `SMALLINT` | Nullable until complete; check 0–100 |
| `report_json` | `JSONB` | Nullable until a validated report exists |
| `question_context_json` | `JSONB` | Nullable reserved field; unused because Q-01/Q-02 are skipped |
| `model_name` | `VARCHAR(100)` | Nullable model identifier used for diagnosis |
| `error_code` | `VARCHAR(80)` | Nullable stable application error code |
| `error_message` | `VARCHAR(500)` | Nullable safe English summary |
| `created_at` | `TIMESTAMPTZ` | Required |
| `started_at` | `TIMESTAMPTZ` | Nullable |
| `completed_at` | `TIMESTAMPTZ` | Nullable |
| `updated_at` | `TIMESTAMPTZ` | Required |

Analysis lifecycle values:

```text
PENDING
RUNNING
WAITING_FOR_USER_INPUT
COMPLETED
FAILED
```

`WAITING_FOR_USER_INPUT` and `question_context_json` remain reserved schema capacity but are not written by
the implemented V1 flow. Q-01/Q-02 were skipped because a target-city question does not materially affect
Resume/JD matching. No separate question table or answer history is created.

### `career_plan`

| Column | Type | Constraints / purpose |
|---|---|---|
| `id` | `BIGINT` | Primary key, generated |
| `user_id` | `BIGINT` | Required FK to `app_user.id` |
| `analysis_report_id` | `BIGINT` | Required FK; unique in V1 |
| `title` | `VARCHAR(200)` | Required English title |
| `summary` | `TEXT` | Required English overview |
| `duration_days` | `SMALLINT` | Required; default 14 |
| `status` | `VARCHAR(20)` | `ACTIVE`, `COMPLETED`, or `ARCHIVED` |
| `created_at` | `TIMESTAMPTZ` | Required |
| `updated_at` | `TIMESTAMPTZ` | Required |

### `plan_task`

| Column | Type | Constraints / purpose |
|---|---|---|
| `id` | `BIGINT` | Primary key, generated |
| `career_plan_id` | `BIGINT` | Required FK to `career_plan.id` |
| `title` | `VARCHAR(200)` | Required |
| `description` | `TEXT` | Required |
| `status` | `VARCHAR(20)` | `TODO`, `IN_PROGRESS`, `COMPLETED`, or `SKIPPED` |
| `due_date` | `DATE` | Required |
| `priority` | `VARCHAR(20)` | `LOW`, `MEDIUM`, or `HIGH` |
| `source_evidence` | `TEXT` | Required report/JD evidence for the task |
| `completed_at` | `TIMESTAMPTZ` | Nullable |
| `archived_at` | `TIMESTAMPTZ` | Nullable; set when a current `TODO` task is replaced by regeneration |
| `created_at` | `TIMESTAMPTZ` | Required |
| `updated_at` | `TIMESTAMPTZ` | Required |

The validated task type and canonical focus area are generation-time validation fields rather than
separate V1 columns. The concrete deliverable is persisted as part of `description`. Current-task reads
require `archived_at IS NULL`; archived rows are internal replacement history.

### `interview_session` — IP-01

| Column | Type | Constraints / purpose |
|---|---|---|
| `id` | `BIGINT` | Primary key, generated |
| `user_id` | `BIGINT` | Required FK to `app_user.id` |
| `analysis_report_id` | `BIGINT` | Required FK; unique so one Analysis has at most one question set |
| `title` | `VARCHAR(200)` | Required fixed English label |
| `created_at` | `TIMESTAMPTZ` | Required |

### `interview_question` — IP-01

| Column | Type | Constraints / purpose |
|---|---|---|
| `id` | `BIGINT` | Primary key, generated |
| `interview_session_id` | `BIGINT` | Required FK to `interview_session.id` |
| `question_order` | `SMALLINT` | Required, unique within the session, range 1–8 |
| `question_type` | `VARCHAR(30)` | `TECHNICAL_GAP`, `PROJECT_FOLLOW_UP`, or `BEHAVIORAL_EVIDENCE` |
| `question_text` | `TEXT` | Required English interview question |
| `assessment_goal` | `TEXT` | Required explanation of what the question evaluates |
| `source_evidence` | `TEXT` | Required exact validated Resume/report/JD evidence |
| `preparation_tip` | `TEXT` | Required evidence-safe preparation guidance, not a generated answer |
| `created_at` | `TIMESTAMPTZ` | Required |

IP-01 question sets are immutable. Answer text, completion state, scores, feedback, audio, and RAG
retrieval data are intentionally not represented in these tables.

Server-issued evidence IDs used during generation are request-lifetime validation references only. They are
type-scoped, resolved to the exact validated source text before insertion, and are not persisted in either
IP-01 table. This keeps `source_evidence` as the auditable source of truth without a schema change.

## 4. Required indexes and ownership rules

Initial indexes should be limited to known access paths:

- unique index on normalized `app_user.email`;
- index on each directly owned table's `(user_id, created_at)`;
- index on `analysis_report(user_id, status, created_at)`;
- index on `plan_task(career_plan_id, due_date)`;
- partial index on current `plan_task(career_plan_id, due_date)` where `archived_at IS NULL`.
- index on `interview_session(user_id, created_at)`;
- unique constraint and ordered-read index on `interview_question(interview_session_id, question_order)`.

Every read, update, and delete follows one of these patterns:

```text
direct ownership: resource.id + resource.user_id = currentUser.id
derived ownership: child.id joined through parent.user_id = currentUser.id
```

The API returns `404` for another user's resource rather than revealing that the ID exists.

## 5. Persistence boundaries

- A Resume upload validates and extracts text before insertion. A rejected upload creates no `resume` row.
- Uploaded bytes, original filename, and declared MIME type are request-only data and are not persisted.
- PDF parsing uses a bounded in-memory cache and does not persist parser scratch data or an original-file copy.
- A successful upload uses the existing `resume` columns and begins at `parse_status = NOT_STARTED`, exactly
  like pasted text. It can then use the existing parse and Analysis flow.
- A parse operation writes `parsed_json` only after validation succeeds.
- A failed parse keeps the original text, sets `parse_status = FAILED`, and records a safe retryable error.
- An analysis report becomes `COMPLETED` only after `match_score` and `report_json` pass validation.
- On application startup, analyses abandoned in `PENDING` or `RUNNING` by an earlier process are marked `FAILED` with a safe interruption code; V1 does not add a durable queue.
- The skipped question flow does not write `question_context_json` in V1.
- A career plan, its initial tasks, the validated report, and analysis completion are saved in one database transaction.
- Regeneration archives current `TODO` rows and creates replacements in one transaction. `IN_PROGRESS`,
  `COMPLETED`, and `SKIPPED` rows remain current and protected.
- Protected plus regenerated current tasks must not exceed eight. Current queries never return archived rows.
- SSE is a delivery channel, not the source of truth. Reconnecting reads the persisted state.
- An interview session and all 5–8 validated questions are inserted in one transaction. Rejected model
  output creates no session or question row. Session reads require `session.id + session.user_id`.
- With AI disabled, parse requests stop before `RUNNING`, Analysis creation stops before `PENDING`, and new
  Interview Preparation stops before inserting a session. Existing persisted resources are not rewritten.

## 6. Upload and deferred storage

U-01 adds no table, column, index, or Flyway migration. It intentionally does not store original files,
file paths, filenames, MIME types, upload objects, OCR output, or reconstructed Resume formatting.

IP-01 adds only the two V7 tables above. It does not reuse `analysis_report.question_context_json` or
`WAITING_FOR_USER_INPUT`; those remain unused reserved capacity for the intentionally skipped Q-01/Q-02 flow.

IP-02 is a read/create browser presentation of the same immutable session and adds no table, column, index,
migration, answer state, score, audio, or retrieval data.

RR-01's Review response is computed per request and is not persisted. It stores no review history, source path,
or user-provided knowledge. The endpoint reuses the existing owned `resume` row and requires its validated
`parsed_json` to be complete.

RR-02 keeps that boundary unchanged. Browser state exists only while the Resume detail dialog is open and
is not written to PostgreSQL, the Resume record, browser storage, or another CareerPilot resource.

RR-03 server-issued rule/evidence IDs, retrieved candidates, model selections, rejection categories, fallback
state, and citation resolution exist only during one request. The model selection is not a persisted user
decision or review history.

RAG-01 adds Flyway V8 for the public synthetic knowledge index. `review_knowledge_chunk` contains `id UUID`,
`content TEXT`, `metadata JSON`, and `embedding vector(1024)`. V8 creates the PostgreSQL `vector` extension,
an HNSW cosine embedding index, and a metadata filter index over source ID/version, visibility, and index
version. The table stores only the bundled public synthetic guide's stable chunks and server metadata; it does
not store Resume text, review output, private knowledge, or user ownership state. Re-indexing the fixed guide
does not create a user-visible resource.

PUBLIC-RAG-GUARD-01 adds Flyway V9 and one operational counter table, `public_rag_daily_usage`. Its composite
primary key is `(usage_date, scope, principal_key)`. `scope` is `USER` or `GLOBAL`; user rows contain only a
date-bound HMAC-SHA256 key derived from the verified Clerk subject, while the global row uses the fixed key
`GLOBAL`. The remaining fields are non-negative request, reserved-input-token, and reserved-output-token
counters plus an update timestamp. It stores no raw Clerk identifier, email, IP address, Resume data, prompt,
retrieved context, model response, provider token, or billing record. Locking the global row and then the user
row in one transaction prevents concurrent requests from crossing either daily request limit.

PUBLIC-AUTH-02 adds Flyway V10 identity fields to the existing `app_user` table. `clerk_issuer` and
`clerk_subject` are either both null or both non-blank and are unique as a pair. Clerk sign-in atomically resolves
or creates this mapping and returns the existing `app_user.id BIGINT`, so all established resource foreign keys
and ownership predicates remain unchanged. `email` and `password_hash` become nullable for Clerk-only users;
neither email nor any browser-supplied user ID is used to match or authorize an external identity.

OPS-01 adds no table, column, index, or Flyway migration. AI availability is process configuration, not
persisted user or resource state. Disabling AI does not change historical analyses, reports, plans, tasks,
interview sessions, parsed documents, or upload storage boundaries.

No chat-history or user-document embedding table is created. Any future private or user-owned corpus needs a
separate schema and authorization design; it must not reuse the public synthetic index implicitly.
