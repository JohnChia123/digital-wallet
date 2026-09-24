-- The mock gateway's OWN memory of what it's processed -- separate from the wallet service's
-- idempotency_keys table entirely. txn_id is server-generated (by TransactionService.insert),
-- never client-chosen, so unlike idempotency_keys there's no "same key, different body" case to
-- detect: a repeated txn_id can only ever mean "retry of this exact payment."
CREATE TABLE gateway_payments (
    txn_id     UUID PRIMARY KEY,
    wallet_id  UUID           NOT NULL,
    amount     NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    status     VARCHAR(16)    NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
