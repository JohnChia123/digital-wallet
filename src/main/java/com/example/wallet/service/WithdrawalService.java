package com.example.wallet.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.wallet.entity.Transaction;
import com.example.wallet.exception.InvalidOtpException;

@Service
public class WithdrawalService {
    // Simulated per the skill ("do not build a real SMS/email OTP service") -- a single fixed
    // valid code, matching the skill's own example value. Not a real OTP flow.
    private static final String SIMULATED_VALID_OTP = "123456";

    private final TransactionService transactionService;
    private final PaymentProcessor paymentProcessor;

    public WithdrawalService(TransactionService transactionService, PaymentProcessor paymentProcessor) {
        this.transactionService = transactionService;
        this.paymentProcessor = paymentProcessor;
    }

    public Transaction withdraw(UUID walletId, String userId, BigDecimal amount, String otp) {
        if (!SIMULATED_VALID_OTP.equals(otp)) {
            throw new InvalidOtpException();
        }

        // Locks the wallet, validates ACTIVE + balance, debits, and inserts the PENDING
        // transaction row all in one DB transaction (see TransactionService.initiateWithdrawal).
        Transaction txn = transactionService.initiateWithdrawal(walletId, userId, amount);

        paymentProcessor.processWithdrawAsync(txn.getId(), walletId, userId, amount);

        return txn;
    }
}
