package com.example.wallet.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.wallet.entity.Transaction;

@Service
public class DepositService {
    private final TransactionService transactionService;
    private final PaymentProcessor paymentProcessor;

    public DepositService(TransactionService transactionService, PaymentProcessor paymentProcessor) {
        this.transactionService = transactionService;
        this.paymentProcessor = paymentProcessor;
    }

    public Transaction deposit(UUID walletId, String userId, BigDecimal amount, String idempotencyKey) {
        // Credits the wallet and inserts the PENDING transaction row in one DB transaction (see
        // TransactionService.initiateDeposit). The gateway call is kicked off only after that has
        // committed, so the async thread never looks for a row that isn't visible yet.
        Transaction txn = transactionService.initiateDeposit(walletId, userId, amount);

        paymentProcessor.processDepositAsync(txn.getId(), walletId, userId, amount, idempotencyKey);

        return txn;
    }
}
