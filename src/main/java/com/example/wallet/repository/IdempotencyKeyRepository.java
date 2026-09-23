package com.example.wallet.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.wallet.entity.IdempotencyKey;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {
    Optional<IdempotencyKey> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);
}
