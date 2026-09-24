package com.example.wallet.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.wallet.dto.PaymentRequest;
import com.example.wallet.dto.PaymentResponse;
import com.example.wallet.service.MockGatewayService;

import jakarta.validation.Valid;

/** The mock external payout gateway. Always succeeds -- failure modes land with 3d. */
@RestController
@RequestMapping("/withdraws")
public class MockWithdrawalController {
    private final MockGatewayService gateway;

    public MockWithdrawalController(MockGatewayService gateway) {
        this.gateway = gateway;
    }

    @PostMapping
    public PaymentResponse withdrawMoney(
            @RequestHeader("Idempotency-Key") UUID txnId,
            @RequestBody @Valid PaymentRequest paymentRequest) {
        return gateway.process(txnId, paymentRequest.walletId(), paymentRequest.amount());
    }
}
