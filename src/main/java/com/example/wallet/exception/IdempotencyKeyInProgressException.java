package com.example.wallet.exception;

/** Same Idempotency-Key, same request, but the original attempt hasn't finished yet -- retry shortly. */
public class IdempotencyKeyInProgressException extends RuntimeException {
    public IdempotencyKeyInProgressException() {
        super("A request with this Idempotency-Key is already being processed");
    }
}
