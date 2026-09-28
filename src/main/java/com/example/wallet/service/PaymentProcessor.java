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

    public PaymentProcessor(
            RestClient paymentRestClient,
            TransactionService transactionService) {

        this.paymentRestClient = paymentRestClient;
        this.transactionService = transactionService;
    }

    @Async
    public void processDepositAsync(
            UUID txnId,
            UUID walletId,
            String userId,
            BigDecimal amount) {

        PaymentResponse response;
        try {
            response = paymentRestClient.post()
                    .uri("/deposits")
                    .headers(h -> h.set("Idempotency-Key", txnId.toString()))
                    .body(new PaymentRequest(
                            walletId,
                            amount,
                            "USD"
                    ))
                    .retrieve()
                    .body(PaymentResponse.class);
        } catch (Exception e) {
            // Per the skill's timeout rule: a client-side exception (timeout, connection issue,
            // gateway unreachable, ...) does NOT mean the payment failed -- the gateway may have
            // actually succeeded and the response just never arrived. Do NOT compensate here;
            // leave the transaction PENDING. (3f adds retrying this instead of giving up after
            // one attempt.)
            System.err.println(
                    "Deposit gateway call failed, leaving PENDING: " + e.getMessage()
            );
            return;
        }

        if (response.transactionStatus() == TransactionStatus.COMPLETED) {
            transactionService.confirmPayment(txnId, walletId, userId, amount);
        } else {
            // A FAILED response is a genuine synchronous decline from the gateway -- definite,
            // not a communication problem -- so it's compensated immediately.
            transactionService.failPayment(txnId, walletId, userId, amount);
        }
    }

    /**
     * Withdrawal's money already moved (debited) before this runs, in the opposite direction from
     * deposit -- so success needs no wallet touch (confirmWithdrawal), and failure needs a refund
     * (failWithdrawal), not a debit reversal. Same reasoning as processDepositAsync otherwise.
     */
    @Async
    public void processWithdrawAsync(
            UUID txnId,
            UUID walletId,
            String userId,
            BigDecimal amount) {

        PaymentResponse response;
        try {
            response = paymentRestClient.post()
                    .uri("/withdraws")
                    .headers(h -> h.set("Idempotency-Key", txnId.toString()))
                    .body(new PaymentRequest(
                            walletId,
                            amount,
                            "USD"
                    ))
                    .retrieve()
                    .body(PaymentResponse.class);
        } catch (Exception e) {
            System.err.println(
                    "Withdrawal gateway call failed, leaving PENDING: " + e.getMessage()
            );
            return;
        }

        if (response.transactionStatus() == TransactionStatus.COMPLETED) {
            transactionService.confirmWithdrawal(txnId);
        } else {
            transactionService.failWithdrawal(txnId, walletId, userId, amount);
        }
    }
}
