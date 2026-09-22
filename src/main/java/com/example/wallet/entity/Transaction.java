package com.example.wallet.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "transactions")
public class Transaction {
    @Id
    private UUID id;
    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;
    @Column(nullable = false)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;
    @Column(name = "external_reference")
    private String externalReference;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Transaction() {}

    public Transaction(UUID walletId, TransactionType type, BigDecimal amount,
                       TransactionStatus status, String externalReference, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.walletId = walletId;
        this.type = type;
        this.amount = amount;
        this.status = status;
        this.externalReference = externalReference;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public Transaction(UUID walletId, TransactionType type, BigDecimal amount, 
                       TransactionStatus status) {
        this.id = UUID.randomUUID();
        this.walletId = walletId;
        this.type = type;
        this.amount = amount;
        this.status = status;
        this.externalReference = "";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    public UUID getId() { return id; }
    public UUID getWalletId() { return walletId; }
    public TransactionType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public TransactionStatus getStatus() { return status; }
    public String getExternalReference() { return externalReference; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setStatus(TransactionStatus status) {
        this.status = status;
    }
}
