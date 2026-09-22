-- Sum of amounts credited by deposits still awaiting gateway confirmation (status = PENDING).
-- available = balance - reserved; withdrawals must check against that, not raw balance,
-- so a pending deposit's money can't be spent before it's actually confirmed.
ALTER TABLE wallets ADD COLUMN reserved NUMERIC(19, 2) NOT NULL DEFAULT 0
    CHECK (reserved >= 0)
    CHECK (reserved <= balance);
