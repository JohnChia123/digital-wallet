package com.example.wallet.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.wallet.entity.Transaction;
import com.example.wallet.entity.TransactionType;

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

    public Transaction deposit(UUID walletId, String userId, BigDecimal amount) {
        walletService.deposit(walletId, userId, amount);
        Transaction txn = transactionService.insert(walletId, TransactionType.DEPOSIT, amount);

        paymentProcessor.processDepositAsync(txn.getId(), walletId, userId, amount);

        return txn;
    }
}
