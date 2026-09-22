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

        // // Call payment service
        // paymentWebClient.post().
        //     uri("/deposits").
        //     bodyValue(new PaymentRequest(walletId, amount, "USD")).
        //     retrieve().
        //     bodyToMono(PaymentResponse.class).
        //     timeout(Duration.ofSeconds(10)).
        //     doOnSuccess(response -> {
        //         /*
        //         Triggers when payment succeeds
        //         Modify transaction row to be completed
        //          */
        //         System.out.println("Payment processed successfully: " + response.transactionStatus());
        //         transactionService.insertTransactionStatus(txnId, TransactionStatus.COMPLETED);
                
        //     })
        //     .doOnError(error -> {
        //         /*
        //         Triggers when payment fails
        //         1. Modify transaction row to be failed
        //         2. Deduct amount from balance
        //         */
        //         System.err.println("Payment failed: " + error.getMessage());
        //         transactionService.insertTransactionStatus(txnId, TransactionStatus.FAILED);
        //         walletService.revertDeposit(walletId, userId, amount);
        //     });
        
        return wallet;
    }
}
