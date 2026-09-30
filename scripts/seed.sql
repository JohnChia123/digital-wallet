-- Sample data for local development and demos. Safe to re-run: it deletes and recreates only the
-- seeded users (bob, carol), leaving any other data -- e.g. alice/mallory from the Postman
-- collection -- untouched.
--
-- Run (after the app has started once, so Flyway has created the schema):
--   docker compose exec -T postgres psql -U wallet -d digital_wallet < scripts/seed.sql
--
-- Inserts rows directly rather than going through the API so transactions can be backdated across
-- the last 30 days (to exercise date filters and pagination) and include every status.
--
-- bob:   ACTIVE wallet, 40 transactions -- deposits and withdrawals, COMPLETED/FAILED, plus one
--        recent PENDING deposit (a gateway timeout awaiting reconciliation) held in `reserved`.
-- carol: SUSPENDED wallet with a few transactions -- withdrawals return 409 WALLET_NOT_ACTIVE.
--
-- Amounts in the last 24h/7d stay well under the default limits, so new deposits and withdrawals
-- against bob's wallet still succeed.

BEGIN;

-- Clear previous seed (children first: transactions -> wallets -> users).
DELETE FROM transactions WHERE wallet_id IN (SELECT id FROM wallets WHERE user_id IN ('bob', 'carol'));
DELETE FROM wallets WHERE user_id IN ('bob', 'carol');
DELETE FROM idempotency_keys WHERE user_id IN ('bob', 'carol');
DELETE FROM users WHERE id IN ('bob', 'carol');

INSERT INTO users (id, created_at) VALUES
    ('bob',   now() - interval '35 days'),
    ('carol', now() - interval '35 days');

-- Balances start at 0 and are set from the transactions below.
INSERT INTO wallets (id, user_id, balance, reserved, status, created_at, updated_at) VALUES
    ('b0b00000-0000-0000-0000-000000000001', 'bob',   0, 0, 'ACTIVE',    now() - interval '35 days', now()),
    ('ca401000-0000-0000-0000-000000000001', 'carol', 0, 0, 'SUSPENDED', now() - interval '35 days', now());

-- bob: 40 transactions, one every ~18 hours going back ~30 days (n = 1 is the most recent).
--   every 3rd one is a withdrawal, the rest are deposits
--   every 9th one is FAILED (a declined gateway call -- never affected the balance)
--   n = 1 is a PENDING deposit
INSERT INTO transactions (id, wallet_id, type, amount, status, external_reference, created_at, updated_at)
SELECT gen_random_uuid(),
       'b0b00000-0000-0000-0000-000000000001',
       CASE WHEN n % 3 = 0 THEN 'WITHDRAWAL' ELSE 'DEPOSIT' END,
       CASE WHEN n % 3 = 0 THEN 20 + (n * 13) % 80      -- withdrawals: 20.00 - 99.00
                           ELSE 50 + (n * 37) % 250 END, -- deposits:    50.00 - 299.00
       CASE WHEN n = 1     THEN 'PENDING'
            WHEN n % 9 = 0 THEN 'FAILED'
            ELSE 'COMPLETED' END,
       NULL,
       now() - n * interval '18 hours',
       now() - n * interval '18 hours'
FROM generate_series(1, 40) AS n;

-- carol: a short history before the wallet was suspended.
INSERT INTO transactions (id, wallet_id, type, amount, status, external_reference, created_at, updated_at) VALUES
    (gen_random_uuid(), 'ca401000-0000-0000-0000-000000000001', 'DEPOSIT',    500.00, 'COMPLETED', NULL, now() - interval '20 days', now() - interval '20 days'),
    (gen_random_uuid(), 'ca401000-0000-0000-0000-000000000001', 'WITHDRAWAL', 120.00, 'COMPLETED', NULL, now() - interval '15 days', now() - interval '15 days'),
    (gen_random_uuid(), 'ca401000-0000-0000-0000-000000000001', 'DEPOSIT',    250.00, 'FAILED',    NULL, now() - interval '12 days', now() - interval '12 days');

-- Derive balances with the same rules the app uses: PENDING and COMPLETED both count (money moves
-- before the gateway confirms), FAILED doesn't. reserved = deposits still PENDING.
UPDATE wallets w
SET balance  = t.balance,
    reserved = t.reserved
FROM (
    SELECT wallet_id,
           COALESCE(SUM(CASE WHEN type = 'DEPOSIT' THEN amount ELSE -amount END)
                    FILTER (WHERE status IN ('PENDING', 'COMPLETED')), 0) AS balance,
           COALESCE(SUM(amount) FILTER (WHERE type = 'DEPOSIT' AND status = 'PENDING'), 0) AS reserved
    FROM transactions
    GROUP BY wallet_id
) t
WHERE w.id = t.wallet_id
  AND w.user_id IN ('bob', 'carol');

COMMIT;

SELECT w.user_id, w.id AS wallet_id, w.status, w.balance, w.reserved,
       (SELECT count(*) FROM transactions t WHERE t.wallet_id = w.id) AS transactions
FROM wallets w
WHERE w.user_id IN ('bob', 'carol')
ORDER BY w.user_id;
