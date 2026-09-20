package com.example.wallet.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.wallet.dto.WalletResponse;
import com.example.wallet.service.WalletService;

@RestController
@RequestMapping("/wallets")
public class WalletController {
    private final WalletService service;

    public WalletController(WalletService service) {
        this.service = service;
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
}
