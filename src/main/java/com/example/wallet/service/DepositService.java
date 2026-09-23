package com.example.wallet.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.wallet.entity.Wallet;

@Service
public class DepositService {
    private final WalletService walletService;
    private final TransactionService transactionService;
    private final PaymentProcessor paymentProcessor;

    public DepositService(WalletService walletService, TransactionService transactionService, PaymentProcessor paymentProcessor) {
        this.walletService = walletService;
        this.transactionService = transactionService;
        this.paymentProcessor = paymentProcessor;
    }
    /*
    POST /mock-payments/withdrawals
    GET  /mock-payments/withdrawals/{paymentId}
    GET  /mock-payments/withdrawals/by-idempotency-key/{key}
     */
    public Wallet deposit(UUID walletId, String userId, BigDecimal amount) {
        Wallet wallet = walletService.deposit(walletId, userId, amount);
        UUID txnId = transactionService.insert(walletId, amount);

        paymentProcessor.processPaymentAsync(txnId, walletId, userId, amount);
        
        return wallet;
    }
}
