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
@Table(name = "wallets")
public class Wallet {
    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private String userId;
    @Column(nullable = false)
    private BigDecimal balance;
    @Column(nullable = false)
    private BigDecimal reserved;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletStatus status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Wallet() {}

    public Wallet(String userId) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.balance = BigDecimal.ZERO.setScale(2);
        this.reserved = BigDecimal.ZERO.setScale(2);
        this.status = WalletStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getUserId() { return userId; }
    public BigDecimal getBalance() { return balance; }
    public WalletStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public BigDecimal getReserved() { return reserved; }
    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
    public void setReserved(BigDecimal reserved) {
        this.reserved = reserved;
    }
}
