package com.example.wallet.repository;

import com.example.wallet.entity.Wallet;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    boolean existsByUserId(String userId);

    /**
     * SELECT ... FOR UPDATE: a second concurrent call for the same id blocks until the first
     * transaction commits or rolls back. This is what actually makes withdrawals concurrency-safe
     * -- not an application-level balance check, which two overlapping transactions could both
     * pass before either has written anything.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") UUID id);
}
