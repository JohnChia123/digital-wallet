# Digital Wallet (Iteration 1)

Spring Boot backend: wallet creation and retrieval. Single currency (USD), one wallet per user.

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

## Auth
Bearer JWT, HS256, signed with `WALLET_JWT_SECRET` (simulated identity provider). The JWT `sub` is the user ID.

## API
- `POST /wallets` -> `201` `{walletId, balance, status, createdAt}`; `409 WALLET_ALREADY_EXISTS` if the user already has one.
- `GET /wallets/{walletId}` -> `200`; `404 WALLET_NOT_FOUND` if missing **or owned by another user** (avoids leaking existence).
- Missing/invalid token -> `401`.
- Errors: `{"code": "...", "message": "..."}`.

## Environment variables
`DB_URL`, `DB_USER`, `DB_PASSWORD`, `WALLET_JWT_SECRET` (>= 32 bytes).
