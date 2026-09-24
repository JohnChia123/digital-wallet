package com.example.wallet.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.wallet.dto.PaymentRequest;
import com.example.wallet.dto.PaymentResponse;
import com.example.wallet.entity.TransactionStatus;

import jakarta.validation.Valid;

/** The mock external payout gateway. Always succeeds -- failure modes land with 3d. */
@RestController
@RequestMapping("/withdraws")
public class MockWithdrawalController {

    @PostMapping
    public PaymentResponse withdrawMoney(
            @RequestParam(required = true) UUID txnId,
            @RequestBody @Valid PaymentRequest paymentRequest) {
        return new PaymentResponse(txnId, paymentRequest.walletId(), TransactionStatus.COMPLETED);
    }
}
