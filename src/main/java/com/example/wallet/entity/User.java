package com.example.wallet.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {
    @Id
    private String id;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected User() {}

    public User(String id) {
        this.id = id;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
}
