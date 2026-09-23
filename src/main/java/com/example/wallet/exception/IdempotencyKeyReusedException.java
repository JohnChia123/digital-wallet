package com.example.wallet.exception;

/** Same Idempotency-Key, but the request body doesn't match what it was first used with. */
public class IdempotencyKeyReusedException extends RuntimeException {
    public IdempotencyKeyReusedException() {
        super("Idempotency-Key was already used with a different request");
    }
}
