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

/** The mock gateway's own record of a payment it has processed -- see V7's migration comment. */
@Entity
@Table(name = "gateway_payments")
public class GatewayPayment {
    @Id
    private UUID txnId;
    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;
    @Column(nullable = false)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GatewayPayment() {}

    public GatewayPayment(UUID txnId, UUID walletId, BigDecimal amount, TransactionStatus status) {
        this.txnId = txnId;
        this.walletId = walletId;
        this.amount = amount;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public UUID getTxnId() { return txnId; }
    public UUID getWalletId() { return walletId; }
    public BigDecimal getAmount() { return amount; }
    public TransactionStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
