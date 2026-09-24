package com.example.wallet.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.wallet.entity.User;
import com.example.wallet.entity.Wallet;
import com.example.wallet.entity.WalletStatus;
import com.example.wallet.exception.InsufficientBalanceException;
import com.example.wallet.exception.WalletAlreadyExistsException;
import com.example.wallet.exception.WalletNotActiveException;
import com.example.wallet.exception.WalletNotFoundException;
import com.example.wallet.repository.UserRepository;
import com.example.wallet.repository.WalletRepository;

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

    @Transactional 
    public Wallet deposit(UUID walletId, String userId, BigDecimal amount) {
        Wallet wallet = wallets.findById(walletId).filter(w -> w.getUserId().equals(userId)).orElseThrow(WalletNotFoundException::new);
        BigDecimal balance = wallet.getBalance();
        BigDecimal newAmount = balance.add(amount);
        BigDecimal curReserved = wallet.getReserved();
        BigDecimal newReserved = curReserved.add(amount);
        wallet.setBalance(newAmount);
        wallet.setReserved(newReserved);
        return wallets.save(wallet);
    }

    private Wallet revertReserved(Wallet wallet, BigDecimal amount) {
        wallet.setReserved(wallet.getReserved().subtract(amount));
        return wallet;
    }
    
    @Transactional
    public Wallet revertDeposit(UUID walletId, String userId, BigDecimal amount) {
        Wallet wallet = wallets.findById(walletId).filter(w -> w.getUserId().equals(userId)).orElseThrow(WalletNotFoundException::new);
        wallet = revertReserved(wallet, amount);
        wallet.setBalance(wallet.getBalance().subtract(amount));
        return wallets.save(wallet);
    }

    @Transactional
    public Wallet confirmDeposit(UUID walletId, String userId, BigDecimal amount) {
        Wallet wallet = wallets.findById(walletId).filter(w -> w.getUserId().equals(userId)).orElseThrow(WalletNotFoundException::new);
        wallet = revertReserved(wallet, amount);
        return wallets.save(wallet); 
    }

    /**
     * Locks the wallet row (SELECT ... FOR UPDATE) for the duration of this transaction -- a
     * second concurrent withdrawal for the same wallet blocks here until this one commits or
     * rolls back, so two overlapping withdrawals can never both read the same starting balance
     * and both succeed. Validates ACTIVE and sufficient balance, then debits.
     */
    @Transactional
    public Wallet withdraw(UUID walletId, String userId, BigDecimal amount) {
        Wallet wallet = wallets.findByIdForUpdate(walletId)
                .filter(w -> w.getUserId().equals(userId))
                .orElseThrow(WalletNotFoundException::new);
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new WalletNotActiveException();
        }
        // Check available (balance - reserved), not raw balance. `reserved` is the sum of
        // deposits still awaiting gateway confirmation -- money already added to `balance` up
        // front (see WalletService.deposit) but not actually settled yet. If a withdrawal only
        // checked raw balance, a user could withdraw funds from a deposit that later gets
        // declined: the withdrawal would already have paid out, then revertDeposit's compensation
        // would try to subtract the deposit amount from a balance that's no longer there,
        // violating the balance >= 0 constraint. Checking against `available` keeps that money
        // untouchable until the deposit is actually confirmed.
        if (wallet.getBalance().subtract(wallet.getReserved()).compareTo(amount) < 0) {
            throw new InsufficientBalanceException();
        }
        wallet.setBalance(wallet.getBalance().subtract(amount));
        return wallets.save(wallet);
    }

    /** Compensation for a withdrawal whose gateway call definitively failed -- refund the debit. */
    @Transactional
    public Wallet refundWithdrawal(UUID walletId, String userId, BigDecimal amount) {
        Wallet wallet = wallets.findByIdForUpdate(walletId)
                .filter(w -> w.getUserId().equals(userId))
                .orElseThrow(WalletNotFoundException::new);
        wallet.setBalance(wallet.getBalance().add(amount));
        return wallets.save(wallet);
    }
}
