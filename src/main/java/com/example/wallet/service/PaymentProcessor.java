package com.example.wallet.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.example.wallet.dto.PaymentRequest;
import com.example.wallet.dto.PaymentResponse;

@Service
public class PaymentProcessor {

    private final RestClient paymentRestClient;
    private final TransactionService transactionService;

    public PaymentProcessor(
            RestClient paymentRestClient,
            TransactionService transactionService) {

        this.paymentRestClient = paymentRestClient;
        this.transactionService = transactionService;
    }

    @Async
    public void processPaymentAsync(
            UUID txnId,
            UUID walletId,
            String userId,
            BigDecimal amount) {

        PaymentResponse response;
        try {
            response = paymentRestClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/deposits").queryParam("txnId", txnId).build())
                    .body(new PaymentRequest(
                            walletId,
                            amount,
                            "USD"
                    ))
                    .retrieve()
                    .body(PaymentResponse.class);
        } catch (Exception e) {
            // Only the gateway call itself failing lands here -- safe to mark FAILED and reverse.
            System.err.println(
                    "Payment failed: " + e.getMessage()
            );
            transactionService.failPayment(txnId, walletId, userId, amount);
            return;
        }

        // The gateway already confirmed success by this point. If confirmPayment throws, that
        // must NOT be treated as a gateway failure -- the payment genuinely went through; only
        // the bookkeeping about it failed. So this is deliberately outside the try/catch above.
        System.out.println(
                "Payment succeeded: " + response.transactionStatus()
        );
        transactionService.confirmPayment(txnId, walletId, userId, amount);
    }
}