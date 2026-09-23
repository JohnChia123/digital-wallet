-- user_id here is just the JWT subject, not a real reference to `users` -- a caller can hit an
-- idempotency-protected endpoint before ever creating a wallet (and so before a `users` row
-- exists for them), which the FK was wrongly rejecting as if it were a concurrency collision.
ALTER TABLE idempotency_keys DROP CONSTRAINT idempotency_keys_user_id_fkey;
