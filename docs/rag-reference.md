# CareerPilot RAG Reference

## Status and boundary

Resume Review has an explicitly opt-in RAG slice. It indexes only a bundled public synthetic Markdown guide; it
does not load user documents, private material, or a general web corpus. `CAREERPILOT_RAG_ENABLED=false` is the
default. The vector path also requires `CAREERPILOT_AI_ENABLED=true` and a configured DashScope key.

The public read-only demo keeps both flags `false`, remains synthetic-only, and uses the existing lexical or
deterministic Review path. A locally enabled full mode can use vector retrieval, but a failure at indexing,
embedding, retrieval, or model selection safely returns to lexical retrieval or the deterministic fallback.
Review output remains ephemeral: it does not change or persist a Resume, Analysis, plan, or user review history.

`careerpilot-private-knowledge/` remains ignored by both Git and Docker. This milestone does not scan, extract,
embed, transmit, or otherwise load that directory. Private licensed material requires a separate authorization,
privacy, deletion, and re-index design before it can enter a product flow.

## Public synthetic retrieval pipeline

```text
bundled public synthetic Markdown guide
  -> stable section-aware chunks and server metadata
  -> DashScope text-embedding-v3 (1024 dimensions)
  -> Spring AI PgVectorStore / PostgreSQL pgvector
  -> filtered, bounded vector retrieval
  -> model selects server-issued ruleId/evidenceId pairs only
  -> strict validation and server-resolved citation
  -> ephemeral Resume Review response
```

The guide is split deterministically by its public sections. Every chunk has a stable ID and metadata:
`sourceId`, `sourceTitle`, `sourceVersion`, `section`, `chunkIndex`, `contentHash`, `visibility`,
`indexVersion`, and category. `page` is nullable: this Markdown-only source omits it from vector metadata and
the server resolves it as `null` in a citation. The initial source allowlist contains only the bundled synthetic
source; its visibility is `PUBLIC`.

## Storage and indexing

Flyway V8 creates the PostgreSQL `vector` extension and the `review_knowledge_chunk` table:

```text
id UUID primary key
content TEXT
metadata JSON
embedding vector(1024)
```

The table has an HNSW cosine index for embeddings and a metadata filter index for source, version, visibility,
and index version. Both local and production Compose stacks use the pinned PostgreSQL 16 pgvector image. Flyway
is the only schema owner; Spring AI `PgVectorStore` does not initialize schema automatically.

At application readiness, a RAG-enabled process replaces the fixed public index from the bundled guide inside
one database transaction. A failed delete/add or embedding operation rolls back and keeps vector retrieval
unready, so the request path falls back instead of using a partial index. The indexer uses no private directory
and logs no guide, query, provider detail, or embedding. The model name,
dimension, chunking behavior, normalization behavior, and index version form one compatibility contract; a
change needs an explicit re-index/version decision rather than mixing vectors.

## Retrieval contract

The initial retrieval settings are deliberately bounded:

| Setting | Value |
| --- | --- |
| Similarity measure | cosine |
| Top K | 8 |
| Similarity threshold | 0.50 |
| Maximum model context from chunks | 6000 characters |
| Source scope | server allowlist, public synthetic source only |

The `0.50` default is exposed as `CAREERPILOT_RAG_SIMILARITY_THRESHOLD`. Manual calibration on 2026-09-19
verified repeatable vector selection for a combined synthetic Resume and successful category-specific retrieval
for Skills, Projects, Experience, and Education, each with a server-owned citation. Thresholds `0.60` and `0.65`
were too strict for reliable retrieval with this intentionally small four-chunk guide and safely fell back to
lexical review. Any future guide or embedding-model change requires renewed retrieval evaluation before changing
the default.

The server applies the allowlist, source-version, `PUBLIC` visibility, and index-version filter before accepting
a result. It then rejects unknown chunk IDs, mismatched content or metadata, duplicate content hashes, and chunks that would
exceed the context bound. It sorts accepted chunks deterministically. Retrieved guidance is reference text, not
instructions; it cannot override application policy or the model-output contract.

When RAG is disabled, unavailable, low-relevance, or returns no valid chunks, Resume Review continues with the
existing lexical path. If model assistance is unavailable or its output is invalid, the service uses its bounded
deterministic fallback. These states are visible as lexical, vector, or deterministic Review types; a no-match
result is a valid successful response.

## Model, validation, and citations

The chat model never writes a visible recommendation, finding, Resume evidence, source name, citation, URL, or
free-form guidance. It may only select pairs of server-issued `ruleId` and `evidenceId` from the candidate set.
The server rejects duplicate fields, malformed JSON, unknown IDs, duplicate selections, over-count output,
invalid rule/evidence pairs, and evidence that does not match the parsed Resume. The model never supplies a
citation: after validating the retrieved document's ID, exact content, metadata, and hash against the bundled
guide, the server constructs the citation and bounded excerpt from that accepted retrieval result.

The existing `POST /api/resumes/{resumeId}/review` contract stays compatible outside application-level Clerk
mode. Clerk application mode uses the guarded `POST /api/rag/resume/review` route and removes the legacy route
to prevent quota bypass; both return the same response fields and `reviewType` values. A vector-selected success uses
`MODEL_ASSISTED_SYNTHETIC_VECTOR_RAG`; a suggestion may additionally contain a server-resolved citation:

```json
{
  "sourceId": "careerpilot-synthetic-resume-review-v1",
  "sourceTitle": "CareerPilot synthetic resume review guide",
  "sourceVersion": "synthetic-review-guide-v1",
  "section": "Skill context",
  "page": null,
  "chunkIndex": 0,
  "excerpt": "If accurate, clarify how an existing skill was used without adding unverified tools, metrics, outcomes, or responsibilities."
}
```

The frontend displays the retrieval mode and, when present, the citation title, version, section, page or chunk
index, and safe server-provided excerpt. It keeps legacy `sourceTitle`/`sourceId` rendering when citation is
absent. Closing or switching a Resume clears the non-persistent result.

## Security and test expectations

- Authenticate and ownership-check the Resume before review; vector metadata never grants access by itself.
- Treat guide and Resume text as untrusted input. Do not log prompts, chunks, embeddings, credentials, model
  bodies, provider errors, paths, or private material.
- Use deterministic fake embeddings/vector stores and fake model output in default tests; no test needs a live
  provider.
- Verify deterministic chunking/hash metadata, database migration, top-K/threshold/filter/dedupe/context bounds,
  citation provenance and excerpt bounds, injection resistance, validation rejection, fallback behavior, and
  RAG-disabled startup.
- Preserve the public-demo boundary: AI and RAG off, synthetic data only, read-only browser/API behavior, and no
  private knowledge.
