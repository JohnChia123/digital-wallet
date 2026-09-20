package com.example.wallet.service;

import com.example.wallet.entity.User;
import com.example.wallet.entity.Wallet;
import com.example.wallet.exception.WalletAlreadyExistsException;
import com.example.wallet.exception.WalletNotFoundException;
import com.example.wallet.repository.UserRepository;
import com.example.wallet.repository.WalletRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {
    private final WalletRepository wallets;
    private final UserRepository users;

    public WalletService(WalletRepository wallets, UserRepository users) {
        this.wallets = wallets;
        this.users = users;
    }

    /**
     * The existence check gives a friendly error; the UNIQUE(user_id) constraint is what
     * actually guarantees one wallet per user under concurrency (violation -> 409 in the handler).
     */
    @Transactional
    public Wallet create(String userId) {
        if (wallets.existsByUserId(userId)) {
            throw new WalletAlreadyExistsException();
        }
        if (!users.existsById(userId)) {
            users.saveAndFlush(new User(userId));
        }
        return wallets.saveAndFlush(new Wallet(userId));
    }

    /** Returns 404 for both missing and foreign wallets to avoid leaking existence. */
    @Transactional(readOnly = true)
    public Wallet get(UUID walletId, String userId) {
        return wallets.findById(walletId)
                .filter(w -> w.getUserId().equals(userId))
                .orElseThrow(WalletNotFoundException::new);
    }
}
