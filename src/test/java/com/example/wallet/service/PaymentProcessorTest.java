package com.example.wallet.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.web.client.RestClient;

import com.example.wallet.dto.PaymentRequest;
import com.example.wallet.dto.PaymentResponse;
import com.example.wallet.entity.TransactionStatus;

/**
 * Pure Mockito unit test: no Spring context, no real HTTP call, no @Async proxy (the method just
 * runs on the test thread). Verifies processPaymentAsync's own branching in isolation from
 * whether the gateway/wiring is actually reachable — that end-to-end path is DepositApiTest.
 */
class PaymentProcessorTest {
    private final RestClient restClient = mock(RestClient.class, Answers.RETURNS_DEEP_STUBS);
    private final TransactionService transactionService = mock(TransactionService.class);
    private final WalletService walletService = mock(WalletService.class);
    private final PaymentProcessor processor = new PaymentProcessor(restClient, transactionService, walletService);

    private final UUID txnId = UUID.randomUUID();
    private final UUID walletId = UUID.randomUUID();
    private final String userId = "alice";
    private final BigDecimal amount = new BigDecimal("50.00");

    @Test
    void gatewaySuccessMarksTransactionCompleted() {
        when(restClient.post().uri(anyString()).body(any(PaymentRequest.class)).retrieve().body(PaymentResponse.class))
                .thenReturn(new PaymentResponse(txnId, TransactionStatus.COMPLETED));

        processor.processPaymentAsync(txnId, walletId, userId, amount);

        verify(transactionService).insertTransactionStatus(txnId, TransactionStatus.COMPLETED);
        verify(walletService, never()).revertDeposit(any(), any(), any());
    }

    @Test
    void gatewayFailureMarksTransactionFailedAndRevertsDeposit() {
        when(restClient.post().uri(anyString()).body(any(PaymentRequest.class)).retrieve().body(PaymentResponse.class))
                .thenThrow(new RuntimeException("gateway unreachable"));

        processor.processPaymentAsync(txnId, walletId, userId, amount);

        verify(transactionService).insertTransactionStatus(txnId, TransactionStatus.FAILED);
        verify(walletService).revertDeposit(walletId, userId, amount);
    }
}
