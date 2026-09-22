package com.example.wallet.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DepositRequest(
    @NotNull @Positive BigDecimal amount
) {}
