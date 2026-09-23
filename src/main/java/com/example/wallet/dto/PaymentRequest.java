package com.example.wallet.dto;
import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequest(
    UUID walletId,
    BigDecimal amount,
    String currency
) {}
