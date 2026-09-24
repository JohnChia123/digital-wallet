package com.example.wallet.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.web.client.RestClient;

import com.example.wallet.dto.PaymentRequest;
import com.example.wallet.dto.PaymentResponse;
import com.example.wallet.entity.TransactionStatus;

/**
 * Pure Mockito unit test: no Spring context, no real HTTP call, no @Async proxy (the method just
 * runs on the test thread). Verifies processDepositAsync's own branching in isolation from
 * whether the gateway/wiring is actually reachable — that end-to-end path is DepositApiTest.
 */
class PaymentProcessorTest {
    private final RestClient restClient = mock(RestClient.class, Answers.RETURNS_DEEP_STUBS);
    // RequestHeadersSpec<S extends RequestHeadersSpec<S>> is a self-bounded generic -- Mockito's
    // deep stubs can't reliably synthesize a mock for .headers(...)'s return type from that (it
    // works fine for .uri(Function<UriBuilder,URI>), whose return type resolves to a concrete
    // interface via the sub-interface's own `extends UriSpec<RequestBodySpec>` clause, a simpler
    // case for reflection). Wiring this one hop explicitly sidesteps the limitation; everything
    // else still goes through restClient's automatic deep stubs.
    private final RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class, Answers.RETURNS_DEEP_STUBS);
    private final TransactionService transactionService = mock(TransactionService.class);
    private final PaymentProcessor processor = new PaymentProcessor(restClient, transactionService);

    private final UUID txnId = UUID.randomUUID();
    private final UUID walletId = UUID.randomUUID();
    private final String userId = "alice";
    private final BigDecimal amount = new BigDecimal("50.00");

    @BeforeEach
    void wireHeadersHop() {
        when(restClient.post().uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.headers(any())).thenReturn(bodySpec);
    }

    @Test
    void gatewaySuccessMarksTransactionCompleted() {
        when(bodySpec.body(any(PaymentRequest.class)).retrieve().body(PaymentResponse.class))
                .thenReturn(new PaymentResponse(txnId, walletId, TransactionStatus.COMPLETED));

        processor.processDepositAsync(txnId, walletId, userId, amount);

        verify(transactionService).confirmPayment(txnId, walletId, userId, amount);
        verify(transactionService, never()).failPayment(any(), any(), any(), any());
    }

    @Test
    void gatewayFailureMarksTransactionFailedAndRevertsDeposit() {
        when(bodySpec.body(any(PaymentRequest.class)).retrieve().body(PaymentResponse.class))
                .thenThrow(new RuntimeException("gateway unreachable"));

        processor.processDepositAsync(txnId, walletId, userId, amount);

        verify(transactionService).failPayment(txnId, walletId, userId, amount);
        verify(transactionService, never()).confirmPayment(any(), any(), any(), any());
    }

    /**
     * Regression test for the bug where a broad catch treated "bookkeeping failed after the
     * gateway already succeeded" the same as "the gateway itself failed" -- which would have
     * incorrectly reversed a deposit that genuinely went through.
     */
    @Test
    void confirmPaymentFailingAfterGatewaySuccessDoesNotTriggerFailPayment() {
        when(bodySpec.body(any(PaymentRequest.class)).retrieve().body(PaymentResponse.class))
                .thenReturn(new PaymentResponse(txnId, walletId, TransactionStatus.COMPLETED));
        doThrow(new RuntimeException("db blip")).when(transactionService)
                .confirmPayment(txnId, walletId, userId, amount);

        // confirmPayment throwing propagates out of processDepositAsync uncovered -- in production
        // that lands in Spring's SimpleAsyncUncaughtExceptionHandler (an @Async void method can't
        // hand the exception back to a caller). What this test actually asserts is what must NOT
        // happen: failPayment must never run, since that would wrongly reverse a real deposit.
        assertThatThrownBy(() -> processor.processDepositAsync(txnId, walletId, userId, amount))
                .isInstanceOf(RuntimeException.class);

        verify(transactionService, never()).failPayment(any(), any(), any(), any());
    }
}
