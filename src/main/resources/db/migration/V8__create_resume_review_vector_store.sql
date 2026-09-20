CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE review_knowledge_chunk (
    id UUID PRIMARY KEY,
    content TEXT NOT NULL,
    metadata JSON NOT NULL,
    embedding VECTOR(1024) NOT NULL
);

CREATE INDEX ix_review_knowledge_chunk_embedding_cosine
    ON review_knowledge_chunk USING hnsw (embedding vector_cosine_ops);

CREATE INDEX ix_review_knowledge_chunk_metadata_filter
    ON review_knowledge_chunk (
        (metadata ->> 'sourceId'),
        (metadata ->> 'sourceVersion'),
        (metadata ->> 'visibility'),
        (metadata ->> 'indexVersion')
    );
