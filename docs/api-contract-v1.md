# CareerPilot V1 API Contract

Status: Implemented through RR-03 on 2026-09-01.

## 1. Conventions

- Base path: `/api`
- Content type: `application/json`, except Resume upload (`multipart/form-data`) and SSE endpoints.
- Product language: English.
- Resource IDs are JSON numbers backed by `BIGINT`.
- Timestamps use ISO 8601 UTC, for example `2026-08-28T19:30:00Z`.
- Validation and error messages returned to users are English.
- Authentication uses a signed JWT stored in an `HttpOnly`, `Secure`, `SameSite=Strict` cookie in production. Tokens are never placed in an SSE query string or browser storage.
- State-changing requests must come from an allowed application origin; CORS is not treated as authentication.

Successful non-SSE response:

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

`204 No Content` responses are the only success responses without this envelope.

Error response:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "The request contains invalid fields.",
    "fieldErrors": {
      "rawText": "Resume text is required."
    }
  }
}
```

## 2. Service health

```text
GET /api/health
```

This unauthenticated endpoint returns `200 OK` with the plain-text body `ok`. All product endpoints documented
below are CareerPilot-owned; no general chat, autonomous agent, terminal, download, filesystem, or MCP endpoint
is exposed.

## 3. Authentication and profile

### `POST /api/auth/register`

Request:

```json
{
  "email": "candidate@example.com",
  "password": "user-provided-password"
}
```

Response: `201 Created` with the new user's public fields. Passwords and hashes are never returned.
The email is trimmed and lowercased before storage. Registering an existing normalized email returns
`409 EMAIL_ALREADY_REGISTERED`.

### `POST /api/auth/login`

Request uses `email` and `password`. On success, the server sets the authentication cookie and returns the current user.
Invalid credentials return `401 INVALID_CREDENTIALS` without identifying which credential was wrong.

The cookie is named `careerpilot_session`, is `HttpOnly`, uses `SameSite=Strict`, is scoped to
`/api`, and expires with the signed JWT. Local HTTP development leaves `Secure` disabled;
production sets `CAREERPILOT_AUTH_COOKIE_SECURE=true`. Production must also provide a signing
secret of at least 32 UTF-8 bytes through `CAREERPILOT_JWT_SECRET`. Tokens are not returned in JSON.

### `POST /api/auth/logout`

Clears the authentication cookie. Response: `204 No Content`.

### `GET /api/users/me`

Returns the current user and career profile.
Missing, expired, or invalid-signature cookies return `401 AUTHENTICATION_REQUIRED`.

### `PUT /api/users/me/profile`

Request:

```json
{
  "targetRole": "Backend Developer Co-op",
  "targetLocation": "Vancouver, BC",
  "workAuthorization": "User-provided summary",
  "weeklyHours": 10,
  "educationSummary": "Computer Science student"
}
```

Profile fields are optional context. They are not prerequisites for Resume/JD analysis, and
`targetLocation` does not trigger a follow-up question in the implemented V1 flow.

## 4. Resume resources

### `POST /api/resumes`

```json
{
  "title": "Backend Co-op Resume",
  "rawText": "English resume text..."
}
```

Response: `201 Created`. The resource is persisted with `parseStatus = NOT_STARTED`.
Parsing is introduced by D-03; it never runs during this pasted-text CRUD milestone.

### `POST /api/resumes/upload`

Creates a Resume from text safely extracted during one multipart request while preserving the pasted-text
endpoint above.

```text
Content-Type: multipart/form-data
file: required .pdf or .docx
title: optional, maximum 160 characters
```

Response: `201 Created` with the same `ApiResponse<Resume>` representation as `POST /api/resumes`.
When `title` is omitted or blank, the safe default is `Uploaded resume`. The new Resume starts with
`parseStatus = NOT_STARTED`; upload does not call the model. The client continues with
`POST /api/resumes/{resumeId}/parse`, and a completed parse can be selected by the unchanged Analysis API.

Rules:

- the authenticated user becomes the owner, and all later reads/updates retain existing ownership checks;
- maximum file size is 5 MiB and maximum normalized extracted text is 100,000 characters;
- PDF documents are limited to 50 pages and use a bounded 32 MiB in-memory parser cache;
- `.pdf` requires `application/pdf`; `.docx` requires
  `application/vnd.openxmlformats-officedocument.wordprocessingml.document`;
- extension, declared content type, magic bytes, and parser-confirmed document structure must agree;
- empty, damaged, encrypted, macro-enabled, unsupported, or textless files are rejected before insertion;
- a PDF without extractable text returns a safe message explaining that scanned PDFs and OCR are unsupported;
- DOCX extraction reads text only and does not execute macros, follow external relationships, or process
  embedded objects as instructions;
- PDF extraction sorts text by position and reconstructs supported visual word boundaries; original layout
  reconstruction remains out of scope;
- original file bytes, filename, and MIME type are not stored;
- error responses never contain document text, server paths, parser exceptions, credentials, or model output.

### Resume reads

```text
GET /api/resumes
GET /api/resumes/{resumeId}
```

Only records owned by the current user are returned.

Lists are ordered by `createdAt` descending in V1.

### `POST /api/resumes/{resumeId}/parse`

Retries parsing for one owned resume whose current parse state is `NOT_STARTED` or `FAILED`.
V1 permits only one active parse attempt for a resource and returns `409 INVALID_RESOURCE_STATE`
for an invalid state. A successful parse returns `COMPLETED` after storing validated JSON; malformed
model output or an unavailable provider returns the persisted resource in `FAILED` state with a safe
English retry message. The original text is never replaced.

### `DELETE /api/resumes/{resumeId}`

Deletes an unreferenced resume. If a historical report references it, the server returns `409 INVALID_RESOURCE_STATE`; V1 never silently deletes the report.

## 5. Job-description resources

### `POST /api/job-descriptions`

```json
{
  "title": "Example Company — Backend Developer Co-op",
  "rawText": "English job-description text..."
}
```

Creation persists the original text with `parseStatus = NOT_STARTED`; parsing is introduced by D-03.

### Job-description reads

```text
GET /api/job-descriptions
GET /api/job-descriptions/{jobDescriptionId}
DELETE /api/job-descriptions/{jobDescriptionId}
```

Ownership and the `409` historical-report rule match resume resources.

Job-description lists use the same newest-first order as resumes.

### `POST /api/job-descriptions/{jobDescriptionId}/parse`

Uses the same ownership, retry, result, and state rules as resume parsing.

## 6. Analysis resources

### `POST /api/analyses`

Creates and starts one analysis using persisted inputs.

```json
{
  "resumeId": 21,
  "jobDescriptionId": 45
}
```

Response: `202 Accepted`.

```json
{
  "success": true,
  "data": {
    "analysisId": 101,
    "status": "PENDING",
    "eventsUrl": "/api/analyses/101/events"
  },
  "error": null
}
```

The backend persists the analysis before starting model work. V1 uses the existing application process; no message queue is required.
Both selected inputs must belong to the current user and have `parseStatus = COMPLETED`; otherwise
the request returns `409 INVALID_RESOURCE_STATE` without starting model work.

### Analysis reads

```text
GET /api/analyses
GET /api/analyses/{analysisId}
```

A completed resource returns the fixed report structure defined in `product-v1.md`.
It also returns a nullable `planId` after the plan milestone is implemented.

### Follow-up questions

No answer endpoint is implemented in V1. Q-01 and Q-02 were intentionally skipped because target city
does not materially change the Resume/JD match report or evidence-based preparation plan. The reserved
`WAITING_FOR_USER_INPUT` state and `question_context_json` column are unused by the current API.

## 7. Analysis SSE

### `GET /api/analyses/{analysisId}/events`

Content type: `text/event-stream`.

Allowed event names:

```text
progress
report
plan
error
done
```

Progress example:

```text
event: progress
data: {"analysisId":101,"stage":"VALIDATING_INPUTS","message":"Validating resume and job description."}
```

Report example:

```text
event: report
data: {"analysisId":101,"matchScore":50,"matchedSkills":["Programming"],"partialMatches":["Application and System Integration"],"missingSkills":["DevOps and Software Delivery"],"strengths":["Completed a backend project"],"risks":["No deployment evidence"],"recommendations":["Add a deployment result to the project entry"]}
```

Terminal event:

```text
event: done
data: {"analysisId":101,"status":"COMPLETED"}
```

Rules:

- The server sends `done` once for a successful terminal stream.
- A failed analysis sends `error` and then closes the stream; persisted status is `FAILED`.
- On reconnect, the server emits the event that represents the current persisted state: `progress`,
  `report`, or `error`. A completed replay also emits `plan` when available and ends with `done`.
- `done` appears exactly once per successful connection. SSE disconnect never changes analysis ownership or deletes results.
- Error events contain safe application messages, never provider credentials or raw stack traces.

The completed V1 sequence is `progress → report → plan → done`. A connection opened while work is still
pending or running receives the current `progress` state and closes; the client reloads or reconnects to
read the persisted terminal state. Plan generation is part of the analysis workflow in V1; a separate
autonomous plan agent is not introduced.

## 8. Career plans and tasks

```text
GET /api/plans/{planId}
GET /api/plans/{planId}/tasks
PATCH /api/plans/{planId}/tasks/{taskId}
POST /api/plans/{planId}/regenerate-remaining
```

The `plan` SSE event and completed analysis resource both expose the generated `planId`.

`GET /api/plans/{planId}/tasks` returns current, non-archived tasks ordered by due date and ID. A current
plan contains at most eight tasks. Replaced history remains in the database and is not exposed by this
current-task endpoint.

Task update request:

```json
{
  "status": "COMPLETED",
  "dueDate": "2026-09-12"
}
```

Only supplied fields are changed. The server verifies that the task belongs to the plan and the plan belongs to the current user.

`POST /api/plans/{planId}/regenerate-remaining` replaces only current `TODO` tasks. `IN_PROGRESS`,
`COMPLETED`, and `SKIPPED` tasks remain current and protected. Replaced tasks are retained internally with
`archivedAt`, while the response returns only the protected and newly generated current tasks. Protected
plus regenerated current tasks never exceed eight. The regenerated tasks use the persisted match report
and job-description JSON; model output remains subject to the same canonical-gap, deliverable, task-type,
and evidence validation.

Plan generation treats 14 days as a scheduling window, not a task count. It creates at most one task per
canonical gap and supports the internal task types `RESUME_APPLICATION`, `INTERVIEW_STORY`,
`EVIDENCE_VERIFICATION`, and `CONCEPT_LEARNING`. If initial plan generation fails semantic validation
twice, the analysis workflow validates and persists a deterministic evidence-verification fallback instead
of either rejected model output.

## 9. Interview preparation

### `POST /api/analyses/{analysisId}/interview-prep`

Creates one immutable evidence-grounded interview question set for an owned completed Analysis. The request
has no body. First creation returns `201 Created`; a repeated request returns `200 OK` with the unchanged
existing resource and does not call the generator again.

```json
{
  "success": true,
  "data": {
    "id": 301,
    "analysisReportId": 101,
    "title": "Interview Preparation",
    "questions": [
      {
        "id": 401,
        "questionOrder": 1,
        "questionType": "PROJECT_FOLLOW_UP",
        "questionText": "How did you structure the Java REST API described in this project?",
        "assessmentGoal": "Explain a real implementation decision using submitted evidence.",
        "sourceEvidence": "Built a Java REST API",
        "preparationTip": "Review the actual design decision and trade-off; do not add details absent from the project evidence."
      }
    ],
    "createdAt": "2026-09-01T22:30:00Z"
  },
  "error": null
}
```

Rules:

- the Analysis must belong to the current user, be `COMPLETED`, and retain validated parsed Resume/JD inputs;
- each session contains 5–8 ordered questions and at most one session exists per Analysis;
- `TECHNICAL_GAP` may use exact report gap or JD requirement evidence without claiming the candidate has it;
- `PROJECT_FOLLOW_UP` and `BEHAVIORAL_EVIDENCE` require exact Resume `projects` or `workExperience` evidence;
- internally, the model selects a server-issued type-scoped evidence ID rather than copying evidence text;
  the server resolves it to exact validated text before persistence, while the public request and response
  formats remain unchanged and expose only `sourceEvidence`;
- generated questions cannot invent tools, incidents, causes, metrics, results, architectures, or experiences;
- `preparationTip` guides preparation but is not a generated answer or STAR story;
- invalid model output is tried at most twice and is never partially persisted.

### `GET /api/interview-sessions/{sessionId}`

Returns the same immutable session with questions ordered by `questionOrder`. Another user's session returns
`404 RESOURCE_NOT_FOUND`; missing authentication returns `401 AUTHENTICATION_REQUIRED`.

The IP-02 browser uses the POST endpoint for both generation and reopening after a page reload. A `200`
response means the existing session was loaded; the browser renders the same representation and does not
request regeneration. No additional discovery, answer, scoring, voice, or RAG endpoint is introduced.

## 10. Resume review

### `POST /api/resumes/{resumeId}/review`

Runs an ephemeral review of one owned Resume whose parse status is `COMPLETED`. The request has no body.
RR-01 first retrieves matching guidance from the bundled public synthetic rule corpus. RR-03 then asks the
configured chat model to select only from those retrieved candidate pairs. It does not use an embedding
model, vector store, private knowledge source, or free-form generated advice.

Successful response:

```json
{
  "success": true,
  "data": {
    "resumeId": 31,
    "reviewType": "MODEL_ASSISTED_SYNTHETIC_LEXICAL_RULES",
    "knowledgeBaseVersion": "synthetic-review-rules-v1",
    "suggestions": [
      {
        "category": "PROJECTS",
        "priority": "HIGH",
        "finding": "This existing projects evidence may benefit from clearer, truthful context.",
        "resumeEvidence": "Built a Java API project",
        "recommendation": "If accurate, add concise context to this existing project statement without inventing scope, results, tools, or responsibilities.",
        "sourceId": "careerpilot-synthetic-resume-review-v1",
        "sourceTitle": "CareerPilot synthetic resume review guide"
      }
    ]
  },
  "error": null
}
```

Rules:

- `resumeEvidence` is an exact string from the validated parsed Resume;
- up to 12 candidates may be retrieved internally; before model selection this is reduced to at most 6
  candidates and no more than 2 from one category, and the public response has the same limits;
- zero matching suggestions is a valid successful result;
- the model returns only exact server-issued `ruleId` and `evidenceId` pairs; all response text is resolved
  by the server from the retrieved candidate;
- `reviewType` reports model-assisted selection, deterministic fallback, or an empty lexical result using the
  three values documented in the RR-03 backlog;
- recommendations are conditional and cannot add unverified tools, metrics, outcomes, or responsibilities;
- another user's or missing Resume returns `404 RESOURCE_NOT_FOUND`; an incomplete parse returns
  `409 INVALID_RESOURCE_STATE`;
- the response is not persisted and does not modify Resume, Analysis, plan, or interview data;
- private filenames, paths, source documents, rule bodies, and proprietary material are never returned.

RR-03 is lexical retrieval augmented by bounded model selection, not vector RAG. Invalid model output is
attempted at most twice; provider failure or two invalid outputs returns the safe deterministic RR-01 result
instead of a model error. `/careerpilot-private-knowledge/` is ignored by Git but is not loaded by this endpoint.

The model must select 1–6 exact offered rule/evidence pairs and may select no more than 2 from one category.
The deterministic fallback applies the same response limits so repeated technology names cannot expand into a
long list of equivalent Skills suggestions.

The RR-02 browser exposes this endpoint only from a completed Resume's detail dialog. The response remains
ephemeral: closing or switching the dialog clears it, and invoking the review again recomputes the same
review flow. It shows whether model-assisted selection or deterministic fallback produced the result. The
browser does not store suggestions, modify Resume text, or expose an endpoint for private knowledge upload.

## 11. HTTP and application errors

| HTTP status | Application code | Meaning |
|---:|---|---|
| `400` | `VALIDATION_ERROR` | Missing, malformed, or out-of-range fields |
| `400` | `EMPTY_FILE` | The uploaded Resume file has no bytes |
| `400` | `UNSUPPORTED_FILE_TYPE` | The filename extension is not `.pdf` or `.docx` |
| `400` | `FILE_TYPE_MISMATCH` | Extension, declared MIME, signature, or container type does not agree |
| `400` | `INVALID_DOCUMENT` | The PDF/DOCX structure is damaged or cannot be parsed safely |
| `400` | `ENCRYPTED_DOCUMENT` | Encrypted Resume files are unsupported |
| `400` | `UNSAFE_DOCUMENT` | Macro-enabled Resume files are unsupported |
| `400` | `NO_EXTRACTABLE_TEXT` | No usable text was extracted; OCR/scanned PDFs are unsupported |
| `400` | `EXTRACTED_TEXT_TOO_LARGE` | Normalized text exceeds 100,000 characters |
| `401` | `AUTHENTICATION_REQUIRED` | No valid session |
| `401` | `INVALID_CREDENTIALS` | Login credentials were not accepted |
| `404` | `RESOURCE_NOT_FOUND` | Missing or not owned by current user |
| `409` | `INVALID_RESOURCE_STATE` | Operation is invalid for current lifecycle state |
| `409` | `EMAIL_ALREADY_REGISTERED` | An account already exists for the normalized email |
| `422` | `MODEL_OUTPUT_INVALID` | Model response failed validation after bounded retry |
| `429` | `MODEL_RATE_LIMITED` | Provider rate limit reached |
| `413` | `FILE_TOO_LARGE` | Multipart bytes exceed the 5 MiB upload limit |
| `500` | `INTERNAL_ERROR` | Unexpected server failure with no internal details exposed |
| `503` | `AI_UNAVAILABLE` | AI is disabled for this environment; enable it explicitly before starting model-backed work |
| `503` | `MODEL_UNAVAILABLE` | Provider timeout or temporary failure |

For IP-01, `INVALID_RESOURCE_STATE` means the owned Analysis or its parsed inputs are not ready;
`MODEL_OUTPUT_INVALID` means two generated question sets failed structural or evidence validation. Neither
failure creates a partial interview session.

For RR-01, `INVALID_RESOURCE_STATE` means the owned Resume has not completed validated parsing. Review
failures do not create or modify a database row.

When `CAREERPILOT_AI_ENABLED=false` (the default), owned read/CRUD/upload operations remain available.
Model-backed Resume/JD parse requests return `503 AI_UNAVAILABLE` before parse state changes; Analysis creation
returns it before a `PENDING` row is inserted; and a new Interview Preparation request returns it before a
session is inserted. Re-requesting an Interview Preparation session that already exists still returns that
owned immutable session. Resume Review and Preparation Plan regeneration retain their documented deterministic
fallback behavior. The response never contains a provider message, environment variable value, key, path,
prompt, document text, or model output.

## 12. Versioning rule

V1 does not add `/v1` to every URL. The contract is versioned in this document and Git. A URL version is introduced only when an incompatible public contract must coexist with the old one.
