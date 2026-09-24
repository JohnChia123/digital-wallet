package com.example.wallet.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.wallet.entity.Transaction;
import com.example.wallet.entity.TransactionStatus;
import com.example.wallet.entity.TransactionType;
import com.example.wallet.exception.InvalidRequestException;
import com.example.wallet.repository.TransactionRepository;

@Service
public class TransactionService {
    public static final int MAX_PAGE_SIZE = 100;

    private final TransactionRepository txnRepo;
    private final WalletService wallets;

    public TransactionService(TransactionRepository txnRepo, WalletService wallets) {
        this.txnRepo = txnRepo;
        this.wallets = wallets;
    }

    @Transactional(readOnly = true)
    public Page<Transaction> list(UUID walletId, String userId, TransactionFilter f, int page, int size) {
        validate(f, page, size);
        wallets.get(walletId, userId); // 404 if missing or not owned by the caller

        Specification<Transaction> spec = (root, q, cb) -> cb.equal(root.get("walletId"), walletId);
        if (f.type() != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("type"), f.type()));
        if (f.status() != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), f.status()));
        if (f.from() != null) {
            var start = f.from().atStartOfDay().toInstant(ZoneOffset.UTC);
            spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("createdAt"), start));
        }
        if (f.to() != null) { // "to" is inclusive: everything before the start of the next day
            var end = f.to().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            spec = spec.and((r, q, cb) -> cb.lessThan(r.get("createdAt"), end));
        }
        if (f.minAmount() != null) spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("amount"), f.minAmount()));
        if (f.maxAmount() != null) spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("amount"), f.maxAmount()));

        // Newest first; id as tiebreaker so pages are stable.
        var sort = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        return txnRepo.findAll(spec, PageRequest.of(page, size, sort));
    }

    private void validate(TransactionFilter f, int page, int size) {
        if (page < 0) throw new InvalidRequestException("page must be >= 0");
        if (size < 1 || size > MAX_PAGE_SIZE) throw new InvalidRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        LocalDate from = f.from(), to = f.to();
        if (from != null && to != null && from.isAfter(to)) throw new InvalidRequestException("from must not be after to");
        if (f.minAmount() != null && f.maxAmount() != null && f.minAmount().compareTo(f.maxAmount()) > 0)
            throw new InvalidRequestException("minAmount must not exceed maxAmount");
    }

    @Transactional
    public Transaction insert(UUID walletId, BigDecimal amount) {
        Transaction txn = new Transaction(walletId, TransactionType.DEPOSIT, amount, TransactionStatus.PENDING, null, Instant.now());
        return txnRepo.save(txn);
    }

    @Transactional
    public void insertTransactionStatus(UUID txnId, TransactionStatus txnStatus) {
        Transaction txn = txnRepo.getReferenceById(txnId);
        if ( txn.getStatus() == TransactionStatus.COMPLETED ||
            txn.getStatus() == TransactionStatus.FAILED) {
                return;
        }
        txn.setStatus(txnStatus);
        txnRepo.save(txn);
    }

    /**
     * Marks the transaction COMPLETED and releases the wallet's reserved hold atomically -- both
     * land or neither does, so the transaction's recorded status and the wallet's reserved amount
     * can never disagree with each other. Only call this after the gateway has already confirmed
     * success; if this throws, the caller must NOT treat that as a gateway failure (nothing here
     * should trigger failPayment -- the payment genuinely succeeded, only the bookkeeping about it
     * failed).
     */
    @Transactional
    public void confirmPayment(UUID txnId, UUID walletId, String userId, BigDecimal amount) {
        insertTransactionStatus(txnId, TransactionStatus.COMPLETED);
        wallets.confirmDeposit(walletId, userId, amount);
    }

    /**
     * Marks the transaction FAILED and reverses the deposit atomically -- same reasoning as
     * confirmPayment. Only call this when the gateway call itself is what failed.
     */
    @Transactional
    public void failPayment(UUID txnId, UUID walletId, String userId, BigDecimal amount) {
        insertTransactionStatus(txnId, TransactionStatus.FAILED);
        wallets.revertDeposit(walletId, userId, amount);
    }
}
