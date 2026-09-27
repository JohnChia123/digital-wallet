package com.example.wallet.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.wallet.entity.Transaction;
import com.example.wallet.entity.TransactionStatus;
import com.example.wallet.entity.TransactionType;

public interface TransactionRepository
        extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    /**
     * Sum of amounts for transactions of this wallet/type/status(es) created since the given
     * instant. Used for the daily/weekly limit checks -- FAILED transactions are deliberately
     * excluded by the caller (they never actually moved money), PENDING is deliberately included
     * (the app treats PENDING as "money already moved" everywhere else, e.g. deposit credits
     * balance before the gateway confirms -- the limit check stays consistent with that).
     */
    @Query("select coalesce(sum(t.amount), 0) from Transaction t "
            + "where t.walletId = :walletId and t.type = :type and t.status in :statuses and t.createdAt >= :since")
    BigDecimal sumSince(@Param("walletId") UUID walletId, @Param("type") TransactionType type,
                         @Param("statuses") Collection<TransactionStatus> statuses, @Param("since") Instant since);
}
