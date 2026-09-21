CREATE TABLE transactions (
    id                 UUID PRIMARY KEY,
    wallet_id          UUID           NOT NULL REFERENCES wallets (id),
    type               VARCHAR(16)    NOT NULL,
    amount             NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    status             VARCHAR(16)    NOT NULL,
    external_reference VARCHAR(128),
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_transactions_wallet_created ON transactions (wallet_id, created_at DESC);
