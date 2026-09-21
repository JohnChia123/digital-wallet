package com.example.wallet.dto;

import com.example.wallet.entity.Transaction;
import com.example.wallet.entity.TransactionStatus;
import com.example.wallet.entity.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(UUID transactionId, TransactionType type, BigDecimal amount,
                                  TransactionStatus status, String externalReference,
                                  Instant createdAt, Instant updatedAt) {
    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(t.getId(), t.getType(), t.getAmount(), t.getStatus(),
                t.getExternalReference(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
