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

@RestController
@RequestMapping("/deposits")
public class MockPaymentController {
    public MockPaymentController() {

    }

    @PostMapping 
    public PaymentResponse depositMoney(
        @RequestParam(required = true) UUID txnId,
        @RequestBody @Valid PaymentRequest paymentRequest) {
        return new PaymentResponse(txnId, paymentRequest.walletId(), TransactionStatus.COMPLETED);
    }

}
