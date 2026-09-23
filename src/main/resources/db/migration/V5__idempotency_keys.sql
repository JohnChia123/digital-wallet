-- One row per (user, Idempotency-Key) pair. UNIQUE is what actually prevents two concurrent
-- identical requests from both proceeding -- same pattern as UNIQUE(user_id) on wallets in V1.
CREATE TABLE idempotency_keys (
    id               UUID PRIMARY KEY,
    user_id          VARCHAR(64)  NOT NULL REFERENCES users (id),
    idempotency_key  VARCHAR(255) NOT NULL,
    request_hash     VARCHAR(64)  NOT NULL,  -- e.g. SHA-256 hex digest of the normalized request body
    status           VARCHAR(16)  NOT NULL,  -- e.g. IN_PROGRESS / COMPLETED -- see note below
    response         TEXT,                   -- the original response body, replayed verbatim on retry
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_idempotency_user_key UNIQUE (user_id, idempotency_key)
);
