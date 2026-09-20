# CareerPilot V1 Product Definition

Status: Implemented, packaged, and published as a read-only synthetic demo through DEPLOY-02 on 2026-09-11
Primary product language: English
Primary market: Canada

## 1. Product goal

CareerPilot helps an English-speaking software-development Co-op or Junior candidate compare one resume with one job description and turn the gaps into a practical preparation plan.

The V1 outcome is:

```text
English resume text (pasted or extracted from PDF/DOCX) + English job-description text
→ structured match report
→ 14-day preparation plan
→ evidence-grounded interview question set
→ optional public-synthetic Resume Review suggestions with vector, lexical, or deterministic retrieval
```

CareerPilot supports preparation and editing decisions. It does not guarantee interviews, rank candidates for employers, or make hiring decisions.

## 2. Target user

The first target user is:

- applying for Software Developer, Backend Developer, Full-Stack Developer, QA Automation, or closely related Co-op/Junior roles in Canada;
- using an English resume and English job description;
- looking for concrete evidence-based gaps and next actions rather than generic career advice.

V1 does not attempt to support every profession, senior leadership hiring, recruiter workflows, or employer-side candidate screening.

## 3. Core user journey

1. The user creates an account and signs in. The career profile is optional and does not block matching.
2. The user pastes an English resume, or uploads a PDF/DOCX resume whose text can be extracted, and saves it.
3. The user pastes an English job description and saves it.
4. The user starts an analysis using one saved resume and one saved job description.
5. CareerPilot validates both inputs and generates a structured match report.
6. CareerPilot creates a persisted preparation plan scheduled within the next 14 days.
7. The user can return later to view the report and plan, update task status or due dates, and regenerate
   the remaining `TODO` tasks.
8. From one completed Analysis, the user can generate one evidence-grounded set of 5–8 interview questions.

The implemented V1 flow does not ask for a target city. The saved job description already provides the
role context needed by resume/JD matching, and a second city answer does not materially change the current
report or evidence-based plan. Q-01 and Q-02 are therefore intentionally skipped rather than incomplete.

## 4. V1 inputs

### Resume

- A title chosen by the user.
- Pasted plain text in English, or text extracted from an uploaded `.pdf` or `.docx` file.
- Pasted text is preserved. For uploads, the normalized extracted text is saved as the Resume raw text.
- The original uploaded file, original filename, and declared media type are not stored.
- Uploads are limited to 5 MiB and 100,000 normalized extracted characters.
- Text-based PDFs are limited to 50 pages and use bounded, position-aware extraction so visually separated
  words remain readable; original layout reconstruction is not provided.
- Legacy `.doc`, images, OCR, and text recognition for scanned PDFs are not supported.
- Parsed data may include education, skills, projects, work experience, and certifications.

### Job description

- A title chosen by the user.
- Pasted plain text in English.
- The original text is preserved.
- Parsed data may include company, role title, location, responsibilities, required skills, preferred skills, and experience requirements.

Saving Resume/JD raw text does not depend on successful AI parsing. A failed parse remains visible and
retryable, and it never deletes or replaces the user's original text.

PDF/DOCX bytes are handled only during the upload request. Extension, declared content type, file signature,
and document structure must agree before text is saved. Empty, damaged, encrypted, oversized, macro-enabled,
or textless documents are rejected. Document content is untrusted data and is never treated as model
instructions. Upload failures return safe English messages without file content, server paths, or dependency
details, and document text is not written to application logs.

### Career profile

The optional profile stores user-provided context:

- target role;
- target city or province;
- work-authorization summary;
- weekly preparation hours;
- optional education summary.

None of these fields is required to create a Resume/JD analysis in V1. In particular, target city or
province does not trigger a follow-up question and is not used to invent missing context.

## 5. V1 outputs

### Match report

The application consumes a fixed structure rather than free-form Markdown:

```json
{
  "matchScore": 50,
  "matchedSkills": ["Programming"],
  "partialMatches": ["Application and System Integration"],
  "missingSkills": ["DevOps and Software Delivery"],
  "strengths": ["Built and tested a complete backend project"],
  "risks": ["Resume does not show deployment experience"],
  "recommendations": ["Add a concise deployment result to the strongest project entry"]
}
```

Rules:

- `matchScore` is an estimated integer from 0 through 100, calculated locally from a fixed capability taxonomy.
- Each job capability is counted once even when the job description repeats it in multiple detailed responsibilities.
- Matched capabilities contribute 100%, partial capabilities contribute 50%, and missing capabilities contribute 0%.
- `partialMatches` contains job capabilities with related but incomplete resume evidence.
- Every list contains concise English statements.
- Recommendations must refer to evidence found in the submitted resume or job description.
- Missing information must be identified explicitly; the model must not invent experience, skills, education, authorization, or outcomes.

### Preparation plan

- Default duration: 14 days.
- Fourteen days is the due-date window, not a requirement to generate 14 tasks.
- A current plan has at most eight tasks and at most one task per canonical gap.
- Tasks are written in English.
- Each task has a title, description, due date, priority, status, and source evidence.
- The source evidence explains which report gap or job requirement caused the task to be created.
- Every task includes a concrete deliverable.
- Supported internal task types are `RESUME_APPLICATION`, `INTERVIEW_STORY`, `EVIDENCE_VERIFICATION`,
  and `CONCEPT_LEARNING`.
- Strong evidence favors truthful resume/application work or a real interview story. Partial evidence
  favors verification or evidence reuse. Concept learning is reserved for gaps without usable evidence.
- A missing capability never becomes claimed experience. Tools, incidents, root causes, metrics, and
  results absent from the source evidence must not appear in a task.
- Regeneration replaces current `TODO` tasks. `IN_PROGRESS`, `COMPLETED`, and `SKIPPED` tasks are protected;
  replaced rows remain archived history and are not returned as current tasks.
- If generated plan output fails semantic validation twice, a validated deterministic fallback creates at
  most one safe evidence-verification task per normalized gap.

### Interview preparation — IP-01

- One completed Analysis can produce one immutable set of 5–8 ordered English questions.
- Supported types are technical-gap questions, real project follow-ups, and behavioral-evidence questions.
- Every question stores the exact source evidence, assessment goal, and preparation tip.
- Technical-gap questions may ask how the candidate would approach or learn a missing requirement; they do
  not claim the candidate already has that experience.
- Project and behavioral questions require concrete evidence from parsed Resume projects or work experience.
- Generation uses server-issued, type-scoped evidence references so the model cannot alter copied evidence;
  the server resolves the selected reference to the exact validated text before saving or returning it.
- Preparation tips help the user review truthful details. They are not generated answers or STAR stories.
- The generator must not add tools, incidents, root causes, metrics, outcomes, architectures, or experiences
  absent from the validated Resume/report/JD evidence.
- Model output is untrusted and must pass strict structure, count, de-duplication, evidence, and semantic
  validation before the session is persisted.
- In the completed match-report dialog, the user can generate or reopen the immutable question set and see
  each question's type, assessment goal, exact source evidence, and preparation tip.
- Reloading the page does not regenerate questions: the same browser action receives the existing session.

### Resume review — RR-01 through RAG-01

- One owned, successfully parsed Resume can be reviewed through an ephemeral backend API.
- The default path uses the existing public-synthetic lexical rules. When both
  `CAREERPILOT_AI_ENABLED=true` and `CAREERPILOT_RAG_ENABLED=true`, local full mode may retrieve stable chunks
  from the bundled public synthetic Markdown guide using DashScope embeddings and PostgreSQL pgvector.
- Every suggestion cites a verbatim parsed Resume value and uses conditional language that cannot add an
  unverified tool, metric, outcome, responsibility, credential, or experience.
- Retrieval uses the calibrated default similarity threshold `0.50`, is source-allowlisted and de-duplicated,
  and is bounded to eight vector candidates and 6000 context characters before displaying at most six diverse
  suggestions. Results are not persisted or applied to the Resume.
- No paid or proprietary source is included, loaded, exposed, or required. A precisely ignored private
  directory reserves a safe local boundary for a separately authorized future experiment.
- In the completed Resume detail dialog, the user can request and inspect suggestion cards with their priority,
  exact Resume evidence, recommendation, source, retrieval mode, and—when vector retrieval is used—a
  server-resolved citation (title, version, section, page or chunk, bounded excerpt). Closing the dialog clears
  the result, and the page does not save or apply a rewrite.
- After lexical or vector retrieval, the model may select only server-offered rule/evidence ID pairs. It cannot
  write findings, recommendations, evidence, categories, priorities, sources, or citations; all displayed text
  remains server-resolved. Strict JSON, duplicate, unknown-ID, count, pair, and Resume-evidence validation plus
  full retrieved-chunk metadata/hash validation protects this boundary. The server creates citations only from
  accepted retrieved chunks. Invalid output or provider/vector failure uses the existing lexical or deterministic
  fallback.
- The hosted public demo is read-only, synthetic-only, and keeps AI/RAG disabled; it never claims vector use.

## 6. Language policy

- V1 accepts and produces English content.
- User-facing navigation, validation, errors, follow-up questions, reports, plans, and examples are written in English.
- Automatic translation and multilingual report generation are out of scope for V1.
- V1 does not add automatic language detection; the interface clearly states that submitted content must be English.
- Internal developer comments may retain original-project language until the related code is rewritten; this must not leak into the English user experience.

## 7. In scope

- Account registration, login, and per-user data isolation.
- Career profile management.
- Pasted resume text and PDF/DOCX Resume upload; pasted job-description text.
- Saving original text and validated structured results.
- Structured resume/JD match report.
- Report history.
- A persisted 14-day plan with editable task status and due dates.
- Replace-style regeneration for remaining `TODO` tasks.
- SSE progress updates for analysis and plan generation.
- One persisted evidence-grounded interview question set per completed Analysis.
- One ephemeral, retrieval-backed Resume Review response based only on public synthetic guidance.

## 8. Out of scope

- Legacy `.doc`, image upload, OCR, and scanned-PDF text recognition.
- Original-file storage, cloud file storage, Resume format reconstruction, and Resume download.
- Job-board scraping, automatic applications, email automation, or employer integrations.
- Resume rewriting without explicit user review.
- Target-city or other follow-up-question workflow without a concrete missing decision that changes the output.
- Multilingual UI or translation.
- Interview-answer capture, completion state, scoring, generated answers, STAR-story writing, voice simulation,
  and interview knowledge-base RAG.
- Private licensed knowledge ingestion, free-form generated Resume advice, persisted review history, and
  automatic Resume rewriting.
- Redis, message queues, microservices, service discovery, Kubernetes, or distributed processing.
- ReAct/MCP tools, arbitrary terminal execution, unrestricted downloads, and autonomous modification of user data.

## 9. Success criteria

V1 is complete when a new user can:

1. register and sign in;
2. save an English resume and an English job description;
3. generate a valid structured match report;
4. see actionable gaps supported by submitted content;
5. receive and update a persisted plan whose tasks fall within a 14-day window;
6. regenerate remaining `TODO` tasks without losing protected progress or exposing archived tasks;
7. sign out and later recover the same data;
8. remain unable to access another user's records;
9. generate interview questions that cite submitted evidence without turning gaps into claimed experience.
10. request a non-persistent synthetic-guidance Resume Review whose suggestions quote parsed Resume evidence
    without inventing missing facts.

The system must handle invalid model JSON, model timeout, SSE disconnect, parsing failure, and semantically
unsupported plan output without leaving misleading completed records or persisting rejected output.

Local development is AI-optional. The default process starts with model-backed features disabled and keeps
authentication, owned CRUD, PDF/DOCX text extraction, and persisted-resource reads available. Operations that
require a new model result return a safe `AI_UNAVAILABLE` response before creating or changing lifecycle state;
Resume Review and Preparation Plan paths that already define deterministic safe fallbacks keep those fallbacks.
Live model calls require explicit `CAREERPILOT_AI_ENABLED=true` opt-in and a process-provided
`DASHSCOPE_API_KEY`. Vector Resume Review additionally requires `CAREERPILOT_RAG_ENABLED=true`; otherwise the
lexical/deterministic paths remain active.

## 10. Delivery boundaries

- Stage 0 preserves and verifies the original chat/SSE baseline.
- Stages 1–3 establish this product contract, PostgreSQL persistence, and identity.
- Stages 4–5 deliver pasted-text management and the first complete match-report flow.
- Stage 6 follow-up questions (`Q-01`/`Q-02`) are skipped because target city is not needed by the current
  Resume/JD matching workflow.
- Stage 7 adds persisted, evidence-aware preparation plans, task updates, safe regeneration, and fallback.
- Stage 8 adds a separate interview-knowledge RAG feature; it does not replace resume/JD evidence.
- Stage 9 starts with V1-01 deterministic end-to-end acceptance and documentation synchronization;
  deployment remains separate follow-up work.
- Post-V1 IP-01 adds evidence-grounded interview questions from the existing completed Analysis without
  introducing the deferred RAG, answer, scoring, or voice workflows.
- Post-V1 IP-02 displays that immutable question set in the CareerPilot report dialog without adding a new
  backend contract, database state, answer workflow, scoring, voice, or RAG.
- Post-V1 RR-01 adds an isolated public synthetic retrieval demonstration for Resume Review. It adds one
  ephemeral API but no model, vector store, embedding, database state, proprietary corpus, or browser UI.
- Post-V1 RR-02 displays the same ephemeral suggestions in the completed Resume detail dialog without adding
  backend state, a new API, automatic rewriting, private knowledge ingestion, vectors, or model calls.
- Post-V1 RR-03 adds bounded model selection after public synthetic lexical retrieval. Only candidate IDs may
  be generated; all displayed text is server-owned, and deterministic fallback keeps the endpoint available
  without new persistence, private content, embeddings, or vector infrastructure.
- RR-03 returns at most six suggestions and at most two from one category. Deterministic fallback uses the same
  diversity limits, and the model sees only that already-diversified candidate set, so a long Skills list does
  not produce repetitive advice.
- Post-V1 RAG-01 adds the optional public-synthetic vector slice: stable Markdown chunks and metadata, DashScope
  `text-embedding-v3` at 1024 dimensions, PostgreSQL pgvector, server filters, retrieved-chunk validation,
  server-owned citations, and lexical/deterministic fallback. It adds no private knowledge ingestion or persisted
  user Review history.
- Post-V1 OPS-01 makes AI an explicit local runtime capability instead of a startup prerequisite. It changes no
  product data model and never converts an unavailable model into a partially persisted operation.
- Post-V1 PORT-01 packages the existing product for portfolio review with startup instructions, an architecture
  diagram, real synthetic-data screenshots, safety/limitation notes, and a timed demo script. It changes no API,
  database schema, dependency, product state, or runtime behavior.
- Post-V1 DEPLOY-01 adds provider-neutral CI and a production Docker/Compose/Nginx package for an explicitly
  enabled, AI-disabled, synthetic-only read-only demonstration. The demo sign-in and startup fixture exist only
  when demo mode is enabled; normal local and product behavior remains unchanged. Provider account creation,
  DNS, TLS issuance, and public release require separate owner authorization.
- Post-V1 DEPLOY-02 records the owner-authorized publication at `https://careerpilot.hermanlidev.com`. The
  provider-specific topology keeps the frontend host port on loopback behind HTTPS, leaves backend/PostgreSQL
  private, uses only the synthetic fixture, and does not change product behavior, API contracts, persistence,
  dependencies, or the AI-disabled public boundary.
