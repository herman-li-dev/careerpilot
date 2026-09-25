# CareerPilot

[![CI](https://github.com/herman-li-dev/careerpilot/actions/workflows/ci.yml/badge.svg)](https://github.com/herman-li-dev/careerpilot/actions/workflows/ci.yml)

CareerPilot is an evidence-grounded job-application preparation workspace. It turns a user's Resume and job
description into a validated match report, a persisted preparation plan, and evidence-aware interview questions
without inventing experience, tools, metrics, or outcomes.

**[Open the hosted demo](https://careerpilot.hermanlidev.com)** ·
**[Portfolio guide](docs/portfolio-guide.md)** ·
**[API contract](docs/api-contract-v1.md)**

![CareerPilot match report](docs/assets/portfolio/03-match-report.png)

## Product flow

```text
Resume + job description
        -> safe text extraction and structured parsing
        -> evidence-calibrated match report
        -> persisted 14-day preparation plan
        -> evidence-grounded interview questions
```

## Core capabilities

- Cookie-authenticated accounts with per-user data isolation.
- Pasted Resume/JD CRUD plus safe PDF/DOCX Resume text extraction.
- Structured parsing, canonical skill-gap matching, and a 0–100 estimated match.
- SSE report progress, persisted plans, task updates, regeneration, and archived-task isolation.
- Evidence-grounded interview questions and non-persistent Resume clarity review, with an opt-in public-synthetic
  vector RAG path and lexical/deterministic fallback.
- Strict model-output validation, deterministic safe fallbacks, privacy-safe logging, and AI-optional startup.

## Technology

| Layer | Technology |
| --- | --- |
| Frontend | Vue 3, Vue Router, Axios, Vite |
| Identity | Existing CareerPilot sessions; optional Clerk Google sign-in boundary for public RAG |
| Backend | Java 21, Spring Boot 3.4.4, Spring JDBC, Spring AI Alibaba |
| Data | PostgreSQL 16 with pgvector, Flyway migrations |
| Documents | Apache PDFBox, Apache POI |
| Delivery | Docker Compose, Nginx, GitHub Actions, DigitalOcean |

## Engineering highlights

- **Evidence before persistence:** model JSON is checked for duplicate fields, structure, semantics, and exact
  source evidence before a report, plan, or interview session can be accepted.
- **Safe failure boundaries:** unavailable models fail before partial lifecycle records are created; bounded plan
  and Resume Review workflows can use deterministic evidence-based fallbacks.
- **Ownership by default:** authentication and repository/service ownership checks keep every Resume, job
  description, Analysis, plan, task, and interview session isolated to its user.
- **Safe document ingestion:** upload validation checks extension, MIME type, file signature, parser-confirmed
  structure, size, extracted-text length, encryption, and textless/scanned-PDF outcomes. Original files are not
  stored.
- **Bounded retrieval:** the optional vector path indexes only a bundled public synthetic guide in PostgreSQL
  pgvector, filters and validates returned chunks, and resolves citations on the server.
- **Deliberate architecture:** CareerPilot remains a modular monolith with one application database; it does not
  add queues or microservices.

The complete architecture, screenshots, design decisions, safety boundaries, and 3–5 minute walkthrough are in
the [portfolio guide](docs/portfolio-guide.md). The implemented REST and SSE behavior is documented in the
[API contract](docs/api-contract-v1.md).

## Hosted read-only demo

The hosted environment contains only a fixed synthetic Resume, job description, report, plan, and interview
question set. AI calls, uploads, registration, report creation, and other persistent mutations are disabled.

The public demo is deployed on a small DigitalOcean host behind an HTTPS reverse proxy. See the
[read-only demo deployment guide](docs/deployment-guide.md) for the verified topology and update procedure.
The production Compose stack exposes only its frontend Nginx on host loopback, creates an isolated synthetic
account on startup, keeps AI and RAG off, and rejects non-whitelisted writes in both Nginx and the backend.

## Local startup

Prerequisites: Java 21, Node.js/npm, Docker Desktop (or PostgreSQL 16), and ports 5433, 8123, and 3000.

In PowerShell, start PostgreSQL with a local-only password:

```powershell
$env:CAREERPILOT_DB_PASSWORD='choose-a-local-password'
docker compose up -d postgres
```

The local container is named `careerpilot-github-postgres` and publishes host port 5433, so it cannot
collide with another checkout that runs its own `careerpilot-postgres` container on 5432.

In the repository root, enable Flyway for the local database and start the backend. CareerPilot defaults to
AI-disabled mode, so a model key is not required for authentication, CRUD, PDF/DOCX extraction, or reading
previously saved results:

```powershell
$env:CAREERPILOT_DB_PASSWORD='choose-a-local-password'
$env:CAREERPILOT_DB_MIGRATION_ENABLED='true'
.\mvnw.cmd spring-boot:run
```

In a second terminal, start the Vue application on the default allowed development origin:

```powershell
cd careerpilot-frontend
npm install
npm run dev -- --host localhost --port 3000
```

Open `http://localhost:3000/`. If port 3000 is unavailable, choose another frontend port and set
`CAREERPILOT_CORS_ALLOWED_ORIGINS` to that exact origin before starting the backend.

To use live parsing, new match reports, new interview-question generation, or model-assisted lexical Resume
Review, explicitly enable AI in the backend terminal and provide the provider key through the environment:

```powershell
$env:CAREERPILOT_AI_ENABLED='true'
$env:DASHSCOPE_API_KEY='your-key'
.\mvnw.cmd spring-boot:run
```

To opt into the Resume Review vector path as well, set both AI and RAG flags before startup. It indexes only the
bundled public synthetic Markdown guide with DashScope `text-embedding-v3` at 1024 dimensions into PostgreSQL
pgvector. The calibrated default similarity threshold is `0.50`; override it only through
`CAREERPILOT_RAG_SIMILARITY_THRESHOLD` after retrieval evaluation. If indexing, embedding, retrieval, or model
selection is unavailable, Resume Review safely returns to lexical or deterministic behavior; it never persists
or modifies the Resume.

```powershell
$env:CAREERPILOT_AI_ENABLED='true'
$env:CAREERPILOT_RAG_ENABLED='true'
$env:DASHSCOPE_API_KEY='your-key'
.\mvnw.cmd spring-boot:run
```

In AI-disabled mode, new parsing, Analysis, and Interview Preparation requests safely return
`503 AI_UNAVAILABLE` without creating partial lifecycle records. Resume Review and plan operations retain their
documented deterministic fallback behavior. Never write a real key or database password into repository files.

### Optional application-level Google authentication

`PUBLIC-AUTH-02` upgrades CareerPilot from the legacy email/password Cookie flow to a default-off,
application-level Clerk boundary. When enabled, `/` displays Clerk sign-in, `/app/**` renders only after Clerk
reports a signed-in session, and every personal API request carries a fresh Clerk Bearer JWT. The backend
verifies RS256/JWKS signature, issuer, timestamps, non-blank subject, optional authorized-party claim, and
non-pending session status. It atomically maps `(issuer, subject)` to the existing `app_user.id BIGINT`; Resume,
Analysis, plan, interview, quota, and ownership code continues to use that server-derived internal ID. Browser
supplied user IDs and the legacy session Cookie are not accepted as identity in this mode.
Legacy register, password login, demo-login, and Cookie logout endpoints also return `404` in this mode so they
cannot create a second, unreachable account path beside Google sign-in.

Before enabling it, create a Clerk application, leave Google as the only sign-in/sign-up method, and set the
exact frontend origin as an authorized party. Put backend values in the process environment and frontend values
in `careerpilot-frontend/.env.local`; use the committed `.env.example` files only as field references. The Clerk
secret key is not used by this slice and must never be placed in a Vite variable.

```powershell
$env:CAREERPILOT_CLERK_AUTH_ENABLED='true'
$env:CAREERPILOT_CLERK_ISSUER='https://your-clerk-frontend-api'
$env:CAREERPILOT_CLERK_AUTHORIZED_PARTIES='http://localhost:3000'
```

```dotenv
VITE_CAREERPILOT_AUTH_ENABLED=true
VITE_CLERK_PUBLISHABLE_KEY=pk_test_replace_with_your_publishable_key
```

Google-only availability is enforced in the Clerk Dashboard. Do not turn on these feature flags until all other
sign-in strategies are disabled there. `CAREERPILOT_PUBLIC_RAG_AUTH_ENABLED` and
`VITE_CAREERPILOT_PUBLIC_RAG_AUTH_ENABLED` remain compatibility switches for the earlier RAG-panel-only flow.
`PUBLIC-UPLOAD-01` adds `POST /api/rag/resume/validate` only when both
backend flags are true. It accepts one PDF or DOCX up to 5 MiB, extracts and validates text during the request,
then returns only the document type and character count. It does not save the original file, filename, extracted
text, or Clerk identity and does not call AI or RAG.

`PUBLIC-RAG-GUARD-01` prepares that model-call boundary behind another default-off backend flag. Its defaults are
three reservations per UTC day per Google user, 100 per UTC day globally, two concurrent model requests, 6000
conservative UTF-8 input-budget units, 800 requested output tokens, and 6800 total budget units. PostgreSQL stores only daily counters
and a date-bound HMAC of the verified Clerk subject. Set a separate 32-byte
`CAREERPILOT_PUBLIC_RAG_GUARD_HMAC_SECRET` before enabling the guard; never expose it through Vite. Upload
validation does not consume model-call reservations. `PUBLIC-RAG-LIVE-01` adds the application-authenticated
`POST /api/rag/resume/review` route. It verifies ownership before reserving quota, wraps the complete provider
path in an automatically closed concurrency permit, and disables the legacy unguarded review route in Clerk
application mode. AI, RAG, and guard flags must all be enabled; otherwise the route returns `503 AI_UNAVAILABLE`.

`PUBLIC-RAG-SECURITY-01` hardens the pre-model boundary. DOCX containers have entry-count, per-entry,
total-uncompressed-size, and path-safety checks in addition to POI's ZIP-bomb protection. Public RAG responses
are marked `no-store`, and metadata-only audit events contain a generated request ID, method, fixed route,
status, and latency—never a token, filename, Resume text, prompt, embedding, or response body. The UI accurately
states that validation is not sent to AI. The private-workspace review control separately requires acknowledgement
that bounded Resume evidence is sent to the configured provider and that provider handling follows its own terms.

## Verification

```powershell
.\mvnw.cmd test
cd careerpilot-frontend
npm run build
```

Default automated tests use deterministic fakes and do not call a live model. GitHub Actions runs backend tests
together with normal and read-only-demo frontend production builds on pushes and pull requests. CI validates the
deployable artifact; it does not publish secrets, call a live model, or deploy to a third party.
