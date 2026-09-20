CREATE TABLE users (
    id         VARCHAR(64) PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE wallets (
    id         UUID PRIMARY KEY,
    user_id    VARCHAR(64)    NOT NULL REFERENCES users (id),
    balance    NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    status     VARCHAR(16)    NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_wallets_user UNIQUE (user_id)
);
