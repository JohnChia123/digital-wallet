package com.example.wallet.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.example.wallet.dto.PaymentRequest;
import com.example.wallet.dto.PaymentResponse;
import com.example.wallet.entity.TransactionStatus;

@Service
public class PaymentProcessor {

    private final RestClient paymentRestClient;
    private final TransactionService transactionService;
    private final WalletService walletService;

    public PaymentProcessor(
            RestClient paymentRestClient,
            TransactionService transactionService,
            WalletService walletService) {

        this.paymentRestClient = paymentRestClient;
        this.transactionService = transactionService;
        this.walletService = walletService;
    }

    @Async
    public void processPaymentAsync(
            UUID txnId,
            UUID walletId,
            String userId,
            BigDecimal amount) {

        try {
            PaymentResponse response = paymentRestClient.post()
                    .uri("/deposits")
                    .body(new PaymentRequest(
                            walletId,
                            amount,
                            "USD"
                    ))
                    .retrieve()
                    .body(PaymentResponse.class);

            System.out.println(
                    "Payment succeeded: " + response.transactionStatus()
            );

            transactionService.insertTransactionStatus(
                    txnId,
                    TransactionStatus.COMPLETED
            );

        } catch (Exception e) {

            System.err.println(
                    "Payment failed: " + e.getMessage()
            );

            transactionService.insertTransactionStatus(
                    txnId,
                    TransactionStatus.FAILED
            );

            walletService.revertDeposit(
                    walletId,
                    userId,
                    amount
            );
        }
    }
}