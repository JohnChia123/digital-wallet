package com.example.wallet.exception;

public class WalletAlreadyExistsException extends RuntimeException {
    public WalletAlreadyExistsException() { super("A wallet already exists for this user"); }
}
