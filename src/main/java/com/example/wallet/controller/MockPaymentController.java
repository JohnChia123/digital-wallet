package com.example.wallet.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public PaymentResponse depositMoney(@RequestBody @Valid PaymentRequest paymentRequest) {
        return new PaymentResponse(paymentRequest.txnId(), TransactionStatus.COMPLETED);
    }

}
