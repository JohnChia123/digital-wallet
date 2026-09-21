package com.example.wallet.service;

import com.example.wallet.entity.TransactionStatus;
import com.example.wallet.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionFilter(TransactionType type, TransactionStatus status,
                                LocalDate from, LocalDate to,
                                BigDecimal minAmount, BigDecimal maxAmount) {}
