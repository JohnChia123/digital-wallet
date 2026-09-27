package com.example.wallet.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.wallet.entity.TransactionStatus;
import com.example.wallet.entity.TransactionType;
import com.example.wallet.exception.TransactionLimitExceededException;
import com.example.wallet.repository.TransactionRepository;

/**
 * Configurable daily/weekly deposit and withdrawal limits, from environment variables (never
 * hardcoded business limits, per the skill). Callers are responsible for concurrency safety --
 * this only does the math, it doesn't lock anything itself. WalletService calls this from inside
 * the wallet row lock it already holds for deposit()/withdraw(), which is what actually prevents
 * two concurrent requests from each independently passing the check and together exceeding it.
 */
@Service
public class TransactionLimitService {
    // PENDING counts, same reasoning as everywhere else in this app: a deposit's balance is
    // credited (and a withdrawal's balance is debited) before the gateway confirms, so PENDING
    // already represents money that has moved. FAILED is excluded -- it never actually happened.
    private static final List<TransactionStatus> COUNTED_STATUSES =
            List.of(TransactionStatus.PENDING, TransactionStatus.COMPLETED);

    private final TransactionRepository txnRepo;
    private final BigDecimal dailyDepositLimit;
    private final BigDecimal weeklyDepositLimit;
    private final BigDecimal dailyWithdrawalLimit;
    private final BigDecimal weeklyWithdrawalLimit;

    public TransactionLimitService(
            TransactionRepository txnRepo,
            @Value("${wallet.limits.daily-deposit}") BigDecimal dailyDepositLimit,
            @Value("${wallet.limits.weekly-deposit}") BigDecimal weeklyDepositLimit,
            @Value("${wallet.limits.daily-withdrawal}") BigDecimal dailyWithdrawalLimit,
            @Value("${wallet.limits.weekly-withdrawal}") BigDecimal weeklyWithdrawalLimit) {
        this.txnRepo = txnRepo;
        this.dailyDepositLimit = dailyDepositLimit;
        this.weeklyDepositLimit = weeklyDepositLimit;
        this.dailyWithdrawalLimit = dailyWithdrawalLimit;
        this.weeklyWithdrawalLimit = weeklyWithdrawalLimit;
    }

    /** Throws if adding `amount` to the wallet's recent totals would exceed either limit. */
    public void checkWithinLimit(UUID walletId, TransactionType type, BigDecimal amount) {
        Instant now = Instant.now();

        BigDecimal dailyTotal = txnRepo.sumSince(walletId, type, COUNTED_STATUSES, now.minus(Duration.ofDays(1)));
        if (dailyTotal.add(amount).compareTo(dailyLimitFor(type)) > 0) {
            throw new TransactionLimitExceededException("daily", type.name());
        }

        BigDecimal weeklyTotal = txnRepo.sumSince(walletId, type, COUNTED_STATUSES, now.minus(Duration.ofDays(7)));
        if (weeklyTotal.add(amount).compareTo(weeklyLimitFor(type)) > 0) {
            throw new TransactionLimitExceededException("weekly", type.name());
        }
    }

    private BigDecimal dailyLimitFor(TransactionType type) {
        return type == TransactionType.DEPOSIT ? dailyDepositLimit : dailyWithdrawalLimit;
    }

    private BigDecimal weeklyLimitFor(TransactionType type) {
        return type == TransactionType.DEPOSIT ? weeklyDepositLimit : weeklyWithdrawalLimit;
    }
}
