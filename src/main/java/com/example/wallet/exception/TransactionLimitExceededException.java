package com.example.wallet.exception;

public class TransactionLimitExceededException extends RuntimeException {
    public TransactionLimitExceededException(String period, String type) {
        super("Exceeds " + period + " " + type.toLowerCase() + " limit");
    }
}
