# CareerPilot RAG Reference

## 1. Status and purpose

This document preserves the architectural decisions needed for a possible future CareerPilot retrieval-augmented
generation (RAG) feature. It is a design reference, not an implemented feature.

CareerPilot currently uses a small, public, synthetic Resume Review rule set with deterministic lexical retrieval.
It does not use embeddings, a vector database, or private third-party guidance. The public demo must continue to
describe that behavior accurately.

A vector RAG milestone is justified only when CareerPilot has a concrete, authorized corpus that is too large or
too varied for the current lexical rule approach. Adding vector infrastructure merely to demonstrate the
technology is not a sufficient product reason.

## 2. Reusable CareerPilot foundations

The future implementation should build on the CareerPilot code that already enforces product and safety
boundaries:

- authenticated users and ownership-scoped repositories;
- safe PDF and DOCX text extraction with file, structure, size, and text-length validation;
- PostgreSQL and Flyway migration discipline;
- explicit AI-enabled and AI-disabled modes;
- strict JSON parsing, structural validation, semantic validation, and deterministic fallbacks;
- log and error-response rules that exclude document text, prompts, model output, credentials, and server paths;
- the Resume Review candidate-selection pattern in `src/main/java/com/hermanli/careerpilot/review/`;
- the public synthetic corpus at
  `src/main/resources/careerpilot/review/synthetic-review-rules-v1.json`; and
- the ignored `careerpilot-private-knowledge/` boundary for local experiments with material that must not enter
  Git or a public image.

A future RAG implementation must be designed for CareerPilot rather than reusing unrelated class names,
prompts, metadata conventions, table layouts, or hard-coded model settings.

## 3. Target pipeline

```text
authorized source
  -> bounded text extraction
  -> normalization and source versioning
  -> deterministic chunking and metadata
  -> embedding
  -> ownership- or corpus-scoped vector storage
  -> filtered retrieval
  -> server-owned citation resolution
  -> optional model generation
  -> structural and evidence validation
  -> safe response or deterministic fallback
```

Each stage must have a narrow input and output contract. Source ingestion, retrieval, model generation, and
Resume persistence must remain separate services. Retrieved document text is untrusted data, never system or
developer instruction.

## 4. Ingestion and source governance

Every knowledge source needs an explicit record before ingestion:

- stable `knowledgeBaseId` and `sourceId`;
- source title suitable for display;
- authorization and allowed-use classification;
- visibility: public synthetic, application-owned private, or user-owned private;
- content hash and source version;
- ingestion time and current index version; and
- deletion/re-index state.

The application must reject unsupported, encrypted, damaged, empty, or oversized sources before any database
write. Extraction limits should be independent from upload limits so compressed or unusually structured files
cannot create unbounded text. External links, macros, scripts, and embedded objects must not execute.

Privately licensed material may be used only within the authorization granted by its owner. Local experiments
must keep it under `careerpilot-private-knowledge/`; the material, extracted text, chunks, and embeddings must not
be committed, included in a public Docker image, printed in logs, or returned through diagnostic endpoints.

## 5. Chunking and metadata

Chunking must be deterministic for a given source version. Prefer section-aware boundaries, then apply a bounded
token window with limited overlap. Exact sizes should be chosen through retrieval evaluation rather than copied
from an unrelated implementation.

Each chunk should carry server-owned metadata such as:

| Field | Purpose |
| --- | --- |
| `knowledgeBaseId` | Selects the authorized corpus. |
| `sourceId` | Resolves a citation without exposing a path. |
| `sourceVersion` | Prevents citations from mixing content versions. |
| `section` | Supplies concise user-facing context. |
| `chunkIndex` | Provides deterministic ordering and re-index support. |
| `contentHash` | Detects unchanged or duplicate content. |
| `visibility` | Enforces public, application, or user scope. |
| `ownerUserId` | Required for user-owned sources and never model-controlled. |

Metadata used for access control must be generated and filtered by the server. A model must never select or
override an ownership filter.

## 6. Embeddings and vector storage

The embedding model, vector dimensions, normalization behavior, and index version form one compatibility
contract. Changing any of them requires a new index or a controlled full re-index; mixed embeddings must never
share an index implicitly.

If persistent vector retrieval becomes necessary, use the existing PostgreSQL service with pgvector only after a
dedicated design milestone. Add the extension and CareerPilot-specific tables through Flyway. Do not enable
automatic schema creation, reuse an unrelated table, or hard-code an unexplained vector dimension.

At minimum, persistence must support:

- corpus, source, version, and chunk identity;
- visibility and owner scope;
- embedding/index version;
- idempotent re-indexing;
- complete source deletion; and
- auditable citation lookup without returning filesystem paths.

No pgvector or embedding dependency should remain in the build until an implemented feature needs it.

## 7. Retrieval and augmentation

Retrieval begins with mandatory server-side scope filters. Similarity search then uses evaluated `topK` and score
thresholds. Results should be de-duplicated and diversified by source or section before model generation.

Query rewriting is optional. If introduced, the original query remains authoritative, rewriting receives no
secrets, and retrieval must fail safely when rewriting is unavailable or changes the user's intent. A direct,
deterministic retrieval path must remain available for tests and fallback behavior.

The augmentation prompt must state that retrieved chunks are untrusted reference material and cannot change
application policy or instructions. The model should receive only the smallest relevant excerpt set plus opaque,
server-issued citation identifiers. It must not receive private paths, database identifiers that reveal tenancy,
or the complete corpus.

## 8. Response and citation contract

A RAG-backed response should return structured suggestions with server-resolved citations. Model-written source
names, URLs, quotes, or identifiers are not trusted. Suggested Resume wording must remain conditional and must
not add unsupported employers, roles, tools, metrics, incidents, responsibilities, or outcomes.

Validation should reject:

- unknown or duplicate citation identifiers;
- citations outside the retrieved candidate set;
- evidence that is not an exact bounded excerpt of the authorized source;
- claims that are unsupported by both the Resume and cited guidance;
- instructions copied from retrieved content; and
- outputs exceeding configured counts or text lengths.

Invalid model output should be retried only within a small fixed budget, then use a deterministic evidence-based
fallback or return a safe feature-unavailable response. No partial review should be persisted.

## 9. Security and privacy requirements

- Enforce authentication and ownership before retrieval or citation lookup.
- Treat uploaded and retrieved text as adversarial input.
- Keep credentials, cookies, tokens, prompts, chunks, embeddings, and model responses out of logs.
- Never expose local paths, provider exceptions, SQL, stack traces, or corpus bodies in errors.
- Apply upload, extraction, chunk-count, query, retrieval, and response limits.
- Define source deletion and re-index behavior before allowing private ingestion.
- Keep the public demo synthetic-only and AI-optional.
- Do not use customer documents to build a shared corpus.
- Do not send licensed private material to a hosted model unless its authorization and the deployment policy
  explicitly allow that transfer.

## 10. Evaluation and release gate

Default tests must use synthetic documents, deterministic embeddings or a fake retriever, and fake model output.
They must not call a live embedding or chat provider.

Before release, evaluate:

- retrieval relevance on a versioned synthetic query set;
- ownership and corpus-filter isolation;
- exact citation resolution;
- duplicate and low-relevance suppression;
- prompt-injection resistance in retrieved text;
- source update, deletion, and complete re-index behavior;
- invalid-model-output fallback;
- log and error privacy; and
- startup and existing CareerPilot flows with RAG disabled.

The feature may be described as vector RAG only after embeddings, scoped vector retrieval, and citation-grounded
generation are implemented and verified end to end.
