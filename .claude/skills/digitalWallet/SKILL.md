# Digital Wallet Backend Skill

## Goal

Build a production-style Digital Wallet backend service using Spring Boot.

The system is a standalone backend microservice for an e-commerce platform. Users are already authenticated externally. There is one wallet per user, all monetary values use a single currency, and there is no P2P wallet transfer functionality.

The implementation must prioritize:

* correctness
* clean architecture
* testability
* concurrency safety
* idempotency
* auditability
* clear separation between database transactions and external payment operations

Do NOT attempt to implement the entire system in one iteration.

Implement the project incrementally according to the phases below.

---

# Technology Stack

Use:

* Java 21 or latest stable Java supported by the chosen Spring Boot version
* Spring Boot
* Spring Web
* Spring Data JPA
* Spring Validation
* Spring Security
* PostgreSQL
* Flyway for database migrations
* JUnit 5
* Mockito
* Testcontainers for integration tests where useful
* Maven or Gradle

Prefer Maven unless the existing project already uses Gradle.

Use Docker Compose for local PostgreSQL.

Do not introduce Kafka, Redis, Kubernetes, or other infrastructure unless a later requirement genuinely needs it.

---

# General Engineering Rules

## Architecture

Use a conventional layered architecture:

```text
controller
    ↓
service
    ↓
repository
    ↓
database
```

Recommended package structure:

```text
com.example.wallet

config
controller
dto
entity
exception
repository
service
security
```

Do not place business logic in controllers.

Controllers should:

* parse requests
* perform request validation
* call services
* return HTTP responses

Services should:

* contain business logic
* define transactional boundaries
* enforce business rules

Repositories should:

* handle persistence only

---

# API Conventions

Use JSON request and response bodies.

Use appropriate HTTP status codes.

Examples:

```text
200 OK
201 Created
400 Bad Request
401 Unauthorized
404 Not Found
409 Conflict
422 Unprocessable Entity
500 Internal Server Error
```

All error responses should use a consistent structure, for example:

```json
{
  "code": "WALLET_NOT_FOUND",
  "message": "Wallet not found"
}
```

Do not expose stack traces or internal implementation details through API responses.

---

# Money Representation

Do NOT use `double` or `float` for money.

Prefer:

```java
BigDecimal
```

with an explicit precision/scale in PostgreSQL.

Example:

```text
NUMERIC(19, 2)
```

The assignment uses a single currency, so currency conversion is not required.

The currency is USD.

---

# Authentication Assumption

The assignment states that users are already authenticated using an external identity service.

For the prototype, simulate JWT authentication.

The wallet service should obtain the user's identity from the authenticated JWT rather than trusting a `userId` supplied in the request body.

For example:

```text
JWT subject = user ID
```

The exact authentication implementation may initially be simplified, but controllers and services should be designed so authentication can later be replaced with a real identity provider.

---

# Database

Start with PostgreSQL.

Initial schema:

```text
users
-----
id
created_at

wallets
-------
id
user_id
balance
status
created_at
updated_at
```

A user may have only one wallet.

Enforce this at the database level:

```text
UNIQUE(user_id)
```

Wallet status may initially contain:

```text
ACTIVE
SUSPENDED
CLOSED
```

Only `ACTIVE` is required during the first iteration.

---

# ITERATION 1 — Wallet Management

Only implement wallet creation and wallet retrieval.

Do NOT implement transactions, deposits, withdrawals, idempotency, transaction limits, or payment gateway behavior yet.

The purpose of Iteration 1 is to establish:

* project structure
* database integration
* API conventions
* authentication abstraction
* validation
* exception handling
* testing strategy

---

## API 1 — Create Wallet

```http
POST /wallets
Authorization: Bearer <JWT>
```

No `userId` should be required in the request body.

Determine the user from the authenticated principal.

Example successful response:

```json
{
  "walletId": "550e8400-e29b-41d4-a716-446655440000",
  "balance": 0.00,
  "status": "ACTIVE",
  "createdAt": "2026-09-19T10:00:00Z"
}
```

Return:

```text
201 Created
```

Rules:

* one wallet per user
* wallet starts with balance 0
* wallet starts as ACTIVE
* creation timestamp must be recorded
* duplicate wallet creation must not create another wallet
* the one-wallet-per-user rule must also be enforced using a database unique constraint

For duplicate creation, choose a documented behavior.

Preferred behavior:

```text
409 Conflict
```

with:

```json
{
  "code": "WALLET_ALREADY_EXISTS",
  "message": "A wallet already exists for this user"
}
```

---

## API 2 — Retrieve Wallet

```http
GET /wallets/{walletId}
Authorization: Bearer <JWT>
```

Example response:

```json
{
  "walletId": "550e8400-e29b-41d4-a716-446655440000",
  "balance": 0.00,
  "status": "ACTIVE",
  "createdAt": "2026-09-19T10:00:00Z"
}
```

Rules:

* wallet must exist
* authenticated user must own the wallet
* users must not be able to retrieve another user's wallet
* return 404 or 403 consistently according to the documented security strategy

Prefer avoiding information leakage about wallets owned by other users.

---

# Iteration 1 Tests

Tests are mandatory.

Do not consider Iteration 1 complete until tests pass.

Include both unit tests and integration/API tests where appropriate.

At minimum test:

## Create Wallet

### Successful creation

Given:

```text
authenticated user has no wallet
```

When:

```text
POST /wallets
```

Then:

```text
201 Created
wallet exists in database
balance = 0
status = ACTIVE
correct user owns wallet
createdAt is populated
```

### Duplicate wallet

Given:

```text
authenticated user already has a wallet
```

When:

```text
POST /wallets
```

Then:

```text
409 Conflict
no second wallet is created
```

### Concurrent wallet creation

Verify the database uniqueness constraint prevents two wallets being created for the same user even if two requests arrive concurrently.

Application checks alone must not be relied upon.

### Unauthorized request

When no valid authentication exists:

```text
POST /wallets
```

Then:

```text
401 Unauthorized
```

---

## Retrieve Wallet

### Successful retrieval

Given:

```text
wallet exists
authenticated user owns wallet
```

Then:

```text
GET /wallets/{walletId}
```

returns:

```text
200 OK
correct wallet information
```

### Wallet does not exist

Return:

```text
404 Not Found
```

### Wallet belongs to another user

Ensure User A cannot retrieve User B's wallet.

### Unauthorized request

Return:

```text
401 Unauthorized
```

---

# Iteration 1 Completion Criteria

Iteration 1 is complete only when:

* application starts successfully
* PostgreSQL schema is created through migrations
* `POST /wallets` works
* `GET /wallets/{walletId}` works
* authentication identity is used
* one-wallet-per-user constraint exists
* error responses are standardized
* unit tests pass
* integration tests pass
* README contains instructions for running the application and tests

Stop after Iteration 1 unless explicitly instructed to continue.

---

# ITERATION 2 — Transaction History

After Iteration 1 is complete and verified, add financial transaction persistence and transaction-history APIs.

Add a table:

```text
transactions
------------
id
wallet_id
type
amount
status
external_reference
created_at
updated_at
```

Possible transaction types:

```text
DEPOSIT
WITHDRAWAL
```

Possible statuses:

```text
PENDING
COMPLETED
FAILED
```

At this stage, transaction records may be seeded or created through test fixtures because deposit and withdrawal APIs are not implemented until Iteration 3.

---

## Transaction History API

Use:

```http
GET /wallets/{walletId}/transactions
Authorization: Bearer <JWT>
```

Do not use:

```text
GET /transactions/{walletId}
```

because transactions are treated as a sub-resource belonging to a wallet.

Support:

* pagination
* transaction type
* status
* date range
* minimum amount
* maximum amount

Example:

```http
GET /wallets/{walletId}/transactions?type=WITHDRAWAL&status=COMPLETED&from=2026-09-01&to=2026-09-30&minAmount=10&maxAmount=500
```

Use sensible defaults for pagination.

Prefer cursor pagination if practical.

Offset pagination is acceptable for the prototype if the decision and trade-off are documented.

---

# Iteration 2 Tests

Test:

* transaction list returned successfully
* empty transaction history
* pagination
* filter by transaction type
* filter by status
* filter by date range
* filter by minimum amount
* filter by maximum amount
* combined filters
* user cannot view transactions belonging to another user's wallet
* nonexistent wallet
* invalid query parameters

Repository queries should also have integration tests where appropriate.

Stop after Iteration 2 unless explicitly instructed to continue.

---

# ITERATION 3 — Deposit and Withdrawal

Only after Iterations 1 and 2 are stable, implement money movement.

This is the most important iteration for correctness.

Implement:

```http
POST /wallets/{walletId}/deposits
POST /wallets/{walletId}/withdrawals
```

Both APIs require:

```text
Authorization: Bearer <JWT>
Idempotency-Key: <unique key>
```

Withdrawal additionally requires the OTP simulation specified by the assignment.

---

# Deposit

Example:

```http
POST /wallets/{walletId}/deposits
Authorization: Bearer <JWT>
Idempotency-Key: deposit-123

{
  "amount": 100.00
}
```

Deposit represents money entering the wallet from a mocked external payment provider.

---

# Withdrawal

Example:

```http
POST /wallets/{walletId}/withdrawals
Authorization: Bearer <JWT>
Idempotency-Key: withdrawal-123

{
  "amount": 50.00,
  "otp": "123456"
}
```

The OTP mechanism is simulated.

Do not build a real SMS/email OTP service.

---

# Withdrawal Database Flow

For a withdrawal:

```text
User requests $50 withdrawal
        |
        v
Wallet Service
        |
        v
BEGIN DATABASE TRANSACTION
        |
        +-- Lock wallet row
        |
        +-- Validate wallet ACTIVE
        |
        +-- Validate OTP
        |
        +-- Validate limits
        |
        +-- Verify balance >= $50
        |
        +-- wallet.balance -= $50
        |
        +-- INSERT transaction
        |      type = WITHDRAWAL
        |      amount = 50
        |      status = PENDING
        |
        v
COMMIT
```

The wallet balance update and transaction creation must occur within the SAME database transaction.

Do not interpret the `transactions` table as a separate database.

`wallets` and `transactions` should remain in the same PostgreSQL database.

---

# Concurrent Withdrawals

Concurrent requests must not allow the balance to become negative.

Use a safe concurrency strategy such as:

```sql
SELECT ...
FOR UPDATE
```

inside a database transaction,

or an atomic conditional update such as:

```sql
UPDATE wallets
SET balance = balance - :amount
WHERE id = :walletId
AND balance >= :amount;
```

Document the chosen strategy.

Test concurrent withdrawals.

Example:

```text
Balance = $100

Withdrawal A = $80
Withdrawal B = $80
```

Only one request should succeed.

The wallet must never become:

```text
-$60
```

---

# Mock External Payment Gateway

The assignment requires external deposit and withdrawal gateways to be mocked through APIs implemented by this project.

Do not integrate Stripe, PayPal, or a real banking API.

Create a small mock payment provider.

For example:

```http
POST /mock-payments/deposits
POST /mock-payments/withdrawals
```

Treat the mock provider as though it were an external system.

The wallet database transaction must NOT remain open while making the HTTP request.

---

# Withdrawal External Flow

After the initial wallet database transaction commits:

```text
wallet.balance decreased
transaction.status = PENDING
```

then call:

```text
Wallet Service
      |
      v
Mock Payment Gateway
```

If payment succeeds:

```text
BEGIN

transaction.status:
PENDING -> COMPLETED

COMMIT
```

If payment definitively fails:

```text
BEGIN

wallet.balance += withdrawal amount

transaction.status:
PENDING -> FAILED

COMMIT
```

This refund is the compensation operation.

---

# Important Timeout Rule

A timeout does NOT necessarily mean the payment failed.

Example:

```text
Wallet sends withdrawal request
Payment provider performs payout
Payment provider response is lost
Wallet receives timeout
```

Do not immediately compensate in this situation.

The payment gateway should support idempotency.

For example:

```http
Idempotency-Key: transaction-id
```

Retrying the same gateway call must not create a second payout.

---

# Idempotency

All wallet write APIs must support:

```http
Idempotency-Key
```

Persist idempotency state.

Suggested table:

```text
idempotency_keys
----------------
id
user_id
idempotency_key
request_hash
status
response
created_at
```

Enforce:

```text
UNIQUE(user_id, idempotency_key)
```

If the same request is retried with the same idempotency key:

```text
return the original response
```

If the same idempotency key is reused with a different request body:

```text
reject the request
```

Never perform the financial operation twice.

---

# Transaction Limits

Implement configurable daily and weekly limits.

Read limits from environment variables.

For example:

```text
WALLET_DAILY_WITHDRAWAL_LIMIT
WALLET_WEEKLY_WITHDRAWAL_LIMIT
WALLET_DAILY_DEPOSIT_LIMIT
WALLET_WEEKLY_DEPOSIT_LIMIT
```

Do not hardcode business limits.

Concurrency must not allow simultaneous requests to bypass a limit.

---

# Retry Strategy

Retry only failures that may be transient.

Examples:

```text
timeout
connection reset
HTTP 5xx
```

Do not automatically retry:

```text
400
401
403
invalid request
insufficient funds
invalid OTP
```

Use:

```text
exponential backoff
+
jitter
```

Retries must be combined with idempotency.

---

# Auditability

Financial transaction history must not be silently modified or deleted.

Important state changes should be logged.

Logs should contain useful identifiers such as:

```text
walletId
transactionId
idempotencyKey
request correlation ID
operation
status
```

Do NOT log:

```text
JWT tokens
OTP values
credentials
other sensitive secrets
```

---

# Iteration 3 Tests

Add comprehensive tests for:

## Deposit

* successful deposit
* invalid amount
* duplicate idempotency key
* same idempotency key with different request
* gateway success
* gateway failure
* gateway timeout and retry
* wallet balance updated correctly
* daily limit exceeded
* weekly limit exceeded

## Withdrawal

* successful withdrawal
* insufficient balance
* invalid amount
* invalid OTP
* inactive wallet
* duplicate idempotency key
* gateway success
* gateway permanent failure
* compensation restores wallet balance
* gateway timeout
* idempotent gateway retry
* daily limit exceeded
* weekly limit exceeded
* concurrent withdrawals cannot create negative balance

## Compensation

Explicitly verify:

```text
balance = $100

withdraw $50

database commits:
balance = $50
transaction = PENDING

external payout fails

compensation executes:
balance = $100
transaction = FAILED
```

---

# Testing Philosophy

Do not create tests merely to increase coverage.

Tests should verify business behavior and important failure scenarios.

Use:

```text
Unit tests
    ↓
Service/business logic

Integration tests
    ↓
Database behavior
constraints
locking
repository queries

API tests
    ↓
request/response
validation
authentication
HTTP status codes
```

Use Testcontainers for PostgreSQL tests where actual PostgreSQL behavior matters.

Do not rely entirely on H2 for concurrency or locking behavior because it may behave differently from PostgreSQL.

---

# Documentation

Maintain:

```text
README.md
DESIGN.md
```

README should contain:

* project overview
* architecture diagram
* prerequisites
* Docker setup
* how to run
* how to run tests
* API documentation
* environment variables

DESIGN.md should contain:

* database schema
* ER diagram
* concurrency strategy
* idempotency strategy
* withdrawal/deposit flow
* mock payment gateway design
* compensation strategy
* retry strategy
* security considerations
* scalability discussion
* production improvements

---

# Development Rule

When asked to implement an iteration:

1. Inspect the existing codebase first.
2. Do not rewrite working code unnecessarily.
3. Implement only the requested iteration.
4. Add database migrations when schema changes.
5. Add or update tests.
6. Run all tests.
7. Fix failing tests before considering the iteration complete.
8. Summarize what changed.
9. Clearly state any design decisions or trade-offs.
10. Do not proceed to the next iteration unless explicitly asked.

---

# Current Development Order

Follow this exact order:

```text
ITERATION 1
POST /wallets
GET /wallets/{walletId}
+ tests

        ↓

ITERATION 2
GET /wallets/{walletId}/transactions
pagination
filters
+ tests

        ↓

ITERATION 3
POST /wallets/{walletId}/deposits
POST /wallets/{walletId}/withdrawals
idempotency
concurrency
limits
OTP
mock payment provider
retry
compensation
+ tests
```

Prioritize correctness and understandable code over unnecessary complexity.
