-- Match the history query's sort order (created_at DESC, id DESC) so pages come straight from the index.
DROP INDEX idx_transactions_wallet_created;
CREATE INDEX idx_transactions_wallet_created ON transactions (wallet_id, created_at DESC, id DESC);
