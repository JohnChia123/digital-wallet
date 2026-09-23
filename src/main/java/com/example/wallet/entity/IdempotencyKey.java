package com.example.wallet.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "idempotency_keys", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "idempotency_key"}))
public class IdempotencyKey {
    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private String userId;
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;
    @Column(name = "request_hash", nullable = false)
    private String requestHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdempotencyStatus status;
    @Column(columnDefinition = "TEXT")
    private String response;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyKey() {}

    /** New row starts IN_PROGRESS with no response yet -- see IdempotencyService for why. */
    public IdempotencyKey(String userId, String idempotencyKey, String requestHash) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.status = IdempotencyStatus.IN_PROGRESS;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getUserId() { return userId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public IdempotencyStatus getStatus() { return status; }
    public String getResponse() { return response; }
    public Instant getCreatedAt() { return createdAt; }

    public void complete(String response) {
        this.status = IdempotencyStatus.COMPLETED;
        this.response = response;
    }
}
