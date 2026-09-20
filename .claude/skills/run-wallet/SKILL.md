---
name: run-wallet
description: Launch the iSerious Digital Wallet app (PostgreSQL via Docker + Spring Boot server) and verify it's serving requests. Use when asked to run, start, or test the wallet app, or to confirm a code change works against the real running app.
---

# Run wallet

Spring Boot REST API (`com.example.wallet`) backed by PostgreSQL. Server listens on port 8080; PostgreSQL runs in Docker on port 5432.

## 1. Start PostgreSQL

```bash
docker compose up -d
```

Starts a `postgres:16` container with database `digital wallet` and user/password are both `wallet` (see `docker-compose.yml`). Wait until ready:

```bash
until docker compose exec -T postgres pg_isready -U wallet >/dev/null 2>&1; do sleep 1; done
```

## 2. Run tests (optional)

```bash
mvn test
```

Tests use Testcontainers and start their own throwaway `postgres:16` container (Docker must be running; first run pulls the image and is slower). They don't touch the compose database. Expect `Tests run: 8, Failures: 0`. The `ERROR ... duplicate key` log lines come from the concurrent-creation test and are expected.

## 3. Start the app

```bash
mvn spring-boot:run
```

Run with `run_in_background: true` — it's a long-lived server. Wait for `Started WalletApplication` in the logs (~10s). Flyway applies `db/migration/V*.sql` on startup; `ddl-auto=validate` so the schema must come from migrations.

## 4. Verify it's serving requests

Endpoints require a Bearer JWT (HS256, `sub` = user ID) signed with `WALLET_JWT_SECRET` (default `change-me-change-me-change-me-32b`). Mint one:

```bash
jwt(){ h=$(printf '{"alg":"HS256","typ":"JWT"}'|basenc --base64url -w0|tr -d =); p=$(printf '{"sub":"%s","exp":4102444800}' "$1"|basenc --base64url -w0|tr -d =); s=$(printf '%s.%s' $h $p|openssl dgst -sha256 -hmac 'change-me-change-me-change-me-32b' -binary|basenc --base64url -w0|tr -d =); echo $h.$p.$s; }
A=$(jwt alice)
curl -s -XPOST localhost:8080/wallets -H "Authorization: Bearer $A"          # 201 (409 if alice already has one)
curl -s localhost:8080/wallets/<walletId> -H "Authorization: Bearer $A"      # 200
curl -s -o /dev/null -w '%{http_code}\n' -XPOST localhost:8080/wallets       # 401
```

Endpoints (JSON):
- `POST /wallets` — create wallet for the JWT user (no body)
- `GET /wallets/{walletId}` — get own wallet (404 if missing or owned by someone else)

## Shutdown

Stop the background `mvn spring-boot:run` process, then:

```bash
docker compose down
```

Add `-v` to also drop the Postgres data (only if the user wants a clean database next time — this deletes data).
