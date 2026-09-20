package com.example.wallet.dto;

import com.example.wallet.entity.Wallet;
import com.example.wallet.entity.WalletStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletResponse(UUID walletId, BigDecimal balance, WalletStatus status, Instant createdAt) {
    public static WalletResponse from(Wallet w) {
        return new WalletResponse(w.getId(), w.getBalance(), w.getStatus(), w.getCreatedAt());
    }
}
