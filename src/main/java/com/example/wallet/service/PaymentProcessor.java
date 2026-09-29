package com.example.wallet.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.example.wallet.config.CorrelationIdFilter;
import com.example.wallet.dto.PaymentRequest;
import com.example.wallet.dto.PaymentResponse;
import com.example.wallet.entity.TransactionStatus;

/**
 * Calls the mock gateway and reacts to the outcome:
 *   - COMPLETED response -> confirm (release reserved for deposits; no-op wallet touch for withdrawals)
 *   - FAILED response -> a genuine synchronous decline from the gateway. Definite, not retried:
 *     compensate immediately (revert deposit / refund withdrawal).
 *   - transient exception (timeout, connection issue, HTTP 5xx) -> retried with backoff+jitter,
 *     combined with idempotency (same Idempotency-Key every attempt, so a gateway retry after it
 *     actually succeeded server-side just replays that result instead of paying out twice). If
 *     retries are exhausted, per the skill's explicit timeout rule: do NOT compensate -- the
 *     gateway may have succeeded and we simply never heard back. The transaction is left PENDING.
 *   - non-transient exception (HTTP 4xx -- a malformed request, which is a bug in our own outbound
 *     call, not something retrying fixes) -> not retried, compensated immediately like FAILED.
 */
@Service
public class PaymentProcessor {
    private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);

    private final RestClient paymentRestClient;
    private final TransactionService transactionService;
    private final int maxAttempts;
    private final long baseDelayMs;

    public PaymentProcessor(
            RestClient paymentRestClient,
            TransactionService transactionService,
            @Value("${wallet.payment.retry.max-attempts}") int maxAttempts,
            @Value("${wallet.payment.retry.base-delay-ms}") long baseDelayMs) {

        this.paymentRestClient = paymentRestClient;
        this.transactionService = transactionService;
        this.maxAttempts = maxAttempts;
        this.baseDelayMs = baseDelayMs;
    }

    @Async
    public void processDepositAsync(
            UUID txnId,
            UUID walletId,
            String userId,
            BigDecimal amount,
            String idempotencyKey) {

        Outcome outcome = callWithRetry("/deposits", "DEPOSIT", txnId, walletId, userId, amount, idempotencyKey);
        switch (outcome) {
            case COMPLETED -> transactionService.confirmPayment(txnId, walletId, userId, amount);
            case FAILED -> transactionService.failPayment(txnId, walletId, userId, amount);
            case PENDING -> { /* retries exhausted on a transient failure -- left PENDING, not compensated */ }
        }
    }

    /**
     * Withdrawal's money already moved (debited) before this runs, in the opposite direction from
     * deposit -- so success needs no wallet touch (confirmWithdrawal), and failure needs a refund
     * (failWithdrawal), not a debit reversal.
     */
    @Async
    public void processWithdrawAsync(
            UUID txnId,
            UUID walletId,
            String userId,
            BigDecimal amount,
            String idempotencyKey) {

        Outcome outcome = callWithRetry("/withdraws", "WITHDRAWAL", txnId, walletId, userId, amount, idempotencyKey);
        switch (outcome) {
            case COMPLETED -> transactionService.confirmWithdrawal(txnId);
            case FAILED -> transactionService.failWithdrawal(txnId, walletId, userId, amount);
            case PENDING -> { /* retries exhausted on a transient failure -- left PENDING, not compensated */ }
        }
    }

    private enum Outcome { COMPLETED, FAILED, PENDING }

    private Outcome callWithRetry(
            String path, String operation, UUID txnId, UUID walletId, String userId, BigDecimal amount, String idempotencyKey) {

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                PaymentResponse response = paymentRestClient.post()
                        .uri(path)
                        .headers(h -> h.set("Idempotency-Key", txnId.toString()))
                        .body(new PaymentRequest(walletId, amount, "USD"))
                        .retrieve()
                        .body(PaymentResponse.class);

                boolean success = response.transactionStatus() == TransactionStatus.COMPLETED;
                audit(operation, txnId, walletId, idempotencyKey, attempt,
                        success ? "GATEWAY_SUCCEEDED" : "GATEWAY_DECLINED");
                return success ? Outcome.COMPLETED : Outcome.FAILED;

            } catch (Exception e) {
                boolean retryable = isRetryable(e);
                boolean lastAttempt = attempt == maxAttempts;
                audit(operation, txnId, walletId, idempotencyKey, attempt,
                        (retryable && !lastAttempt) ? "GATEWAY_CALL_FAILED_RETRYING" : "GATEWAY_CALL_FAILED");
                log.warn("Gateway call failed: operation={} transactionId={} attempt={} retryable={} error={}",
                        operation, txnId, attempt, retryable, e.getClass().getSimpleName());

                if (!retryable) {
                    return Outcome.FAILED;
                }
                if (lastAttempt) {
                    return Outcome.PENDING;
                }
                sleepBeforeRetry(attempt);
            }
        }
        return Outcome.PENDING; // unreachable given maxAttempts >= 1, kept for exhaustiveness
    }

    /**
     * Timeouts and connection issues (ResourceAccessException, e.g. a wrapped
     * SocketTimeoutException/ConnectException) and HTTP 5xx are transient -- the gateway may not
     * have even seen the request, or may still succeed on retry. HTTP 4xx means our own outbound
     * request was malformed, which is a bug, not a transient condition -- retrying won't help.
     */
    private boolean isRetryable(Exception e) {
        return e instanceof ResourceAccessException || e instanceof HttpServerErrorException;
    }

    private void sleepBeforeRetry(int attempt) {
        long exponential = baseDelayMs * (1L << (attempt - 1));
        long jitter = ThreadLocalRandom.current().nextLong(baseDelayMs);
        try {
            Thread.sleep(Duration.ofMillis(exponential + jitter));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Structured audit log per the skill: walletId, transactionId, idempotencyKey, correlation ID,
     * operation, status. correlationId comes from MDC (propagated across the @Async boundary by
     * AsyncConfig's TaskDecorator) rather than a parameter, so it's automatically present even on
     * log lines that don't call this method. Never includes OTP values, JWTs, or other secrets --
     * none of those are in scope for a gateway call to begin with.
     */
    private void audit(String operation, UUID txnId, UUID walletId, String idempotencyKey, int attempt, String status) {
        log.info("event=payment_gateway_call operation={} transactionId={} walletId={} idempotencyKey={} "
                        + "correlationId={} attempt={} status={}",
                operation, txnId, walletId, idempotencyKey, MDC.get(CorrelationIdFilter.MDC_KEY), attempt, status);
    }
}
