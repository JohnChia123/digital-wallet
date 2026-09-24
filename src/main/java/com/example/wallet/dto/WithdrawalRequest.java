package com.example.wallet.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record WithdrawalRequest(
    @NotNull @Positive BigDecimal amount,
    @NotBlank String otp
) {}
