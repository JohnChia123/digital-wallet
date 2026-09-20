package com.example.wallet.repository;

import com.example.wallet.entity.Wallet;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    boolean existsByUserId(String userId);
}
