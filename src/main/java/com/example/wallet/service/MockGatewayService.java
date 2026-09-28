package com.example.wallet.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.example.wallet.dto.PaymentResponse;
import com.example.wallet.entity.GatewayPayment;
import com.example.wallet.entity.TransactionStatus;
import com.example.wallet.repository.GatewayPaymentRepository;

/**
 * Shared "external gateway" logic behind both MockPaymentController and MockWithdrawalController.
 * Genuinely tracks what it's already processed, so a repeated call for the same txnId (a retry of
 * the wallet service's own outbound call) replays the original result instead of paying out twice.
 *
 * Behavior for a given call is normally SUCCESS, unless a test has armed GatewaySimulator for this
 * walletId:
 *   - PERMANENT_FAILURE: the gateway processed and declined the request -- a genuine synchronous
 *     response (HTTP 200, status FAILED), not a network-level error. The caller must not retry this.
 *   - TIMEOUT: models the skill's example exactly ("payment provider performs payout, response is
 *     lost, wallet receives timeout") -- the payout is still recorded as COMPLETED here, but the
 *     response is delayed past the client's read timeout so the caller experiences a client-side
 *     timeout exception instead of ever seeing this response. A retry with the same Idempotency-Key
 *     then finds the already-COMPLETED record below and replays it immediately, so a gateway retry
 *     never pays out twice.
 */
@Service
public class MockGatewayService {
    private final GatewayPaymentRepository payments;
    private final GatewaySimulator simulator;
    private final long timeoutMs;

    public MockGatewayService(
            GatewayPaymentRepository payments,
            GatewaySimulator simulator,
            @Value("${wallet.payment.timeout-ms}") long timeoutMs) {
        this.payments = payments;
        this.simulator = simulator;
        this.timeoutMs = timeoutMs;
    }

    /**
     * Deliberately NOT @Transactional at this level. Each repository call below already gets its
     * own transaction from Spring Data's own defaults (SimpleJpaRepository's methods are each
     * @Transactional individually) -- that's all this method actually needs, since it never does
     * more than one repository call per branch. Wrapping the whole method in one shared
     * transaction was a real bug: when saveAndFlush's own proxy throws on the constraint
     * violation, Spring marks that PHYSICAL transaction rollback-only immediately, before the
     * exception even reaches this method's catch block. The fallback findById below would then
     * run inside that same doomed transaction -- it can succeed and return real data, and this
     * method can return normally, and the transaction still fails at commit time with an
     * UnexpectedRollbackException neither piece of code ever threw or caught. Not sharing a
     * transaction here means the fallback read gets its own fresh one, unaffected by the earlier
     * failure.
     */
    public PaymentResponse process(UUID txnId, UUID walletId, BigDecimal amount) {
        var existing = payments.findById(txnId);
        if (existing.isPresent()) {
            GatewayPayment payment = existing.get();
            return new PaymentResponse(txnId, payment.getWalletId(), payment.getStatus());
        }

        GatewaySimulator.Mode mode = simulator.consume(walletId);
        if (mode == GatewaySimulator.Mode.TIMEOUT) {
            sleepPastClientTimeout();
        }
        TransactionStatus status = mode == GatewaySimulator.Mode.PERMANENT_FAILURE
                ? TransactionStatus.FAILED
                : TransactionStatus.COMPLETED;

        try {
            payments.saveAndFlush(new GatewayPayment(txnId, walletId, amount, status));
        } catch (DataIntegrityViolationException e) {
            // A concurrent duplicate call for the same txnId won the race on the primary key --
            // re-read what it stored rather than processing (and paying out) a second time.
            GatewayPayment payment = payments.findById(txnId).orElseThrow();
            return new PaymentResponse(txnId, payment.getWalletId(), payment.getStatus());
        }

        return new PaymentResponse(txnId, walletId, status);
    }

    private void sleepPastClientTimeout() {
        try {
            Thread.sleep(Duration.ofMillis(timeoutMs + 200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
