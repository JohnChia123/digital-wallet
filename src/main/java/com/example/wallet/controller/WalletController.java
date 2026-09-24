package com.example.wallet.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.wallet.dto.DepositRequest;
import com.example.wallet.dto.TransactionResponse;
import com.example.wallet.dto.WalletResponse;
import com.example.wallet.service.DepositService;
import com.example.wallet.service.IdempotencyService;
import com.example.wallet.service.WalletService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/wallets")
public class WalletController {
    private final WalletService service;
    private final DepositService depositService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public WalletController(WalletService service, DepositService depositService,
                             IdempotencyService idempotencyService, ObjectMapper objectMapper) {
        this.service = service;
        this.depositService = depositService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    // Authentication.getName() is the JWT subject = user ID.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WalletResponse create(Authentication auth) {
        return WalletResponse.from(service.create(auth.getName()));
    }

    @GetMapping("/{walletId}")
    @ResponseStatus(HttpStatus.OK)
    public WalletResponse get(@PathVariable UUID walletId, Authentication auth) {
        return WalletResponse.from(service.get(walletId, auth.getName()));
    }

    @PostMapping("/{walletId}/deposits")
    public TransactionResponse deposit(
        @PathVariable UUID walletId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody @Valid DepositRequest request,
        Authentication auth) throws JsonProcessingException {
        String userId = auth.getName();

        var cached = idempotencyService.begin(userId, idempotencyKey, request);
        if (cached.isPresent()) {
            return objectMapper.readValue(cached.get(), TransactionResponse.class);
        }

        TransactionResponse response;
        try {
            response = TransactionResponse.from(depositService.deposit(walletId, userId, request.amount()));
        } catch (RuntimeException e) {
            // The deposit itself never happened -- safe to free the key for a fresh retry.
            idempotencyService.abandon(userId, idempotencyKey);
            throw e;
        }

        // The deposit already committed by this point. If recording that fails, do NOT abandon()
        // -- deleting the key here would let a retry run depositService.deposit(...) a second
        // time for money that already moved. Left IN_PROGRESS: retries get 409 until this is
        // resolved manually, which is safer than silently risking a double deposit. (A production
        // system would want a TTL/expiry + alerting for this case rather than a permanent stall.)
        idempotencyService.complete(userId, idempotencyKey, objectMapper.writeValueAsString(response));
        return response;
    }
}
