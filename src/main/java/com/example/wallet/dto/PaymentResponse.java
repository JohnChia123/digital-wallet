package com.example.wallet.dto;
import java.util.UUID;

import com.example.wallet.entity.TransactionStatus;

public record PaymentResponse (
    UUID txnId,
    TransactionStatus transactionStatus
) {}
