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

@RestController
@RequestMapping("/deposits")
public class MockPaymentController {
    private final MockGatewayService gateway;

    public MockPaymentController(MockGatewayService gateway) {
        this.gateway = gateway;
    }

    @PostMapping
    public PaymentResponse depositMoney(
            @RequestHeader("Idempotency-Key") UUID txnId,
            @RequestBody @Valid PaymentRequest paymentRequest) {
        return gateway.process(txnId, paymentRequest.walletId(), paymentRequest.amount());
    }
}
