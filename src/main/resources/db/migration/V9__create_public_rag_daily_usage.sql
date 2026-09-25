CREATE TABLE public_rag_daily_usage (
    usage_date DATE NOT NULL,
    scope VARCHAR(16) NOT NULL,
    principal_key VARCHAR(64) NOT NULL,
    request_count INTEGER NOT NULL DEFAULT 0,
    reserved_input_tokens BIGINT NOT NULL DEFAULT 0,
    reserved_output_tokens BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (usage_date, scope, principal_key),
    CONSTRAINT ck_public_rag_daily_usage_scope
        CHECK (scope IN ('USER', 'GLOBAL')),
    CONSTRAINT ck_public_rag_daily_usage_principal
        CHECK (
            (scope = 'GLOBAL' AND principal_key = 'GLOBAL')
            OR (scope = 'USER' AND principal_key ~ '^[0-9a-f]{64}$')
        ),
    CONSTRAINT ck_public_rag_daily_usage_counts
        CHECK (request_count >= 0 AND reserved_input_tokens >= 0 AND reserved_output_tokens >= 0)
);
