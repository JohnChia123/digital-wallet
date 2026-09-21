# Digital Wallet (Iterations 1-2)

Spring Boot backend: wallet creation/retrieval and transaction history. Single currency (USD), one wallet per user.

## Prerequisites
Java 21+, Maven, Docker (for local PostgreSQL only).

## Run
```
docker compose up -d
mvn spring-boot:run
```
Flyway creates the schema on startup.

## Tests
```
mvn test
```
Tests use Testcontainers: each run starts a throwaway `postgres:16` container (Docker must be running) and applies the Flyway migrations to it. The first run pulls the image and is slower.

## Pagination
Offset pagination (`page`/`size`) for the prototype: simple and lets clients jump to a page and see totals.
Trade-off: deep pages get slower (the database still walks the skipped rows) and results can shift if new
transactions arrive between requests. Cursor (keyset) pagination on `(created_at, id)` would fix both; the
existing `(wallet_id, created_at DESC)` index already supports it.
Transactions are only created by test fixtures until deposits/withdrawals arrive in Iteration 3.

## Auth
Bearer JWT, HS256, signed with `WALLET_JWT_SECRET` (simulated identity provider). The JWT `sub` is the user ID.

## API
- `POST /wallets` -> `201` `{walletId, balance, status, createdAt}`; `409 WALLET_ALREADY_EXISTS` if the user already has one.
- `GET /wallets/{walletId}` -> `200`; `404 WALLET_NOT_FOUND` if missing **or owned by another user** (avoids leaking existence).
- `GET /wallets/{walletId}/transactions` -> `200` `{items, page, size, totalElements, totalPages}`, newest first.
  Same ownership rule as above (404 for missing/foreign wallets). Query parameters (all optional):
  `type` (DEPOSIT|WITHDRAWAL), `status` (PENDING|COMPLETED|FAILED), `from`/`to` (ISO dates, UTC, both inclusive),
  `minAmount`, `maxAmount`, `page` (default 0), `size` (default 20, max 100).
  Invalid parameters -> `400 INVALID_REQUEST`.
- Missing/invalid token -> `401`.
- Errors: `{"code": "...", "message": "..."}`.

## Environment variables
`DB_URL`, `DB_USER`, `DB_PASSWORD`, `WALLET_JWT_SECRET` (>= 32 bytes).
