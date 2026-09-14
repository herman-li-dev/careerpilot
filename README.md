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
- Evidence-grounded interview questions and non-persistent Resume clarity review.
- Strict model-output validation, deterministic safe fallbacks, privacy-safe logging, and AI-optional startup.

## Technology

| Layer | Technology |
| --- | --- |
| Frontend | Vue 3, Vue Router, Axios, Vite |
| Backend | Java 21, Spring Boot 3.4.4, Spring JDBC, Spring AI Alibaba |
| Data | PostgreSQL 16, Flyway migrations |
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
- **Deliberate architecture:** CareerPilot remains a modular monolith with one application database; it does not
  add queues, microservices, or vector infrastructure without a demonstrated need.

The complete architecture, screenshots, design decisions, safety boundaries, and 3–5 minute walkthrough are in
the [portfolio guide](docs/portfolio-guide.md). The implemented REST and SSE behavior is documented in the
[API contract](docs/api-contract-v1.md).

## Hosted read-only demo

The hosted environment contains only a fixed synthetic Resume, job description, report, plan, and interview
question set. AI calls, uploads, registration, report creation, and other persistent mutations are disabled.

The public demo is deployed on a small DigitalOcean host behind an HTTPS reverse proxy. See the
[read-only demo deployment guide](docs/deployment-guide.md) for the verified topology and update procedure.
The production Compose stack exposes only its frontend Nginx on host loopback, creates an isolated synthetic
account on startup, keeps AI off, and rejects non-whitelisted writes in both Nginx and the backend.

## Local startup

Prerequisites: Java 21, Node.js/npm, Docker Desktop (or PostgreSQL 16), and ports 5432, 8123, and 3000.

In PowerShell, start PostgreSQL with a local-only password:

```powershell
$env:CAREERPILOT_DB_PASSWORD='choose-a-local-password'
docker compose up -d postgres
```

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

To use live parsing, new match reports, or new interview-question generation, explicitly enable AI in the
backend terminal and provide the provider key through the environment:

```powershell
$env:CAREERPILOT_AI_ENABLED='true'
$env:DASHSCOPE_API_KEY='your-key'
.\mvnw.cmd spring-boot:run
```

In AI-disabled mode, new parsing, Analysis, and Interview Preparation requests safely return
`503 AI_UNAVAILABLE` without creating partial lifecycle records. Resume Review and plan operations retain their
documented deterministic fallback behavior. Never write a real key or database password into repository files.

## Verification

```powershell
.\mvnw.cmd test
cd careerpilot-frontend
npm run build
```

Default automated tests use deterministic fakes and do not call a live model.

The current backend suite contains **144 automated tests**. GitHub Actions runs it together with normal and
read-only-demo frontend production builds on pushes and pull requests. CI validates the deployable artifact; it
does not publish secrets, call a live model, or deploy to a third party.
