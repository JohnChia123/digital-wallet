package com.example.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.wallet.entity.Transaction;
import com.example.wallet.entity.TransactionStatus;
import com.example.wallet.entity.Wallet;
import com.example.wallet.repository.TransactionRepository;
import com.example.wallet.repository.WalletRepository;
import com.example.wallet.service.WalletService;

/**
 * Same reasoning as DepositApiTest for DEFINED_PORT + its own Testcontainers container:
 * PaymentProcessor.processWithdrawAsync makes a genuine outbound call back into this same app.
 * Own port (9091, not 9090) -- DepositApiTest also uses DEFINED_PORT with its own separate
 * Testcontainers container, so the two can't share a cached Spring context; both trying to bind
 * the same port when run in the same suite fails with "port already in use".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(properties = {"server.port=9091", "wallet.payment.base-url=http://localhost:9091"})
@AutoConfigureMockMvc
@Testcontainers
class WithdrawalApiTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    static {
        postgres.start();
    }

    @Autowired MockMvc mvc;
    @Autowired WalletService walletService;
    @Autowired WalletRepository wallets;
    @Autowired TransactionRepository transactions;
    @Autowired JdbcTemplate jdbc;

    Wallet wallet;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM idempotency_keys");
        jdbc.update("DELETE FROM transactions");
        jdbc.update("DELETE FROM wallets");
        jdbc.update("DELETE FROM users");
        wallet = walletService.create("alice");
        // Give alice a starting balance to withdraw from -- deposit directly at the DB level so
        // this test doesn't depend on the deposit flow (and its own async settling) to set up state.
        jdbc.update("UPDATE wallets SET balance = 100.00 WHERE id = ?", wallet.getId());
    }

    private RequestPostProcessor as(String user) {
        return jwt().jwt(j -> j.subject(user));
    }

    private ResultActions withdraw(String user, String idempotencyKey, String body) throws Exception {
        return mvc.perform(post("/wallets/" + wallet.getId() + "/withdrawals")
                .with(as(user))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void withdrawalDebitsBalanceImmediatelyAndReturnsPendingTransaction() throws Exception {
        withdraw("alice", "wd-1", "{\"amount\": 40.00, \"otp\": \"123456\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").exists())
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.amount").value(40.00))
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertThat(wallets.findById(wallet.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("60.00"));
    }

    @Test
    void withdrawalEventuallySettlesCompleted() throws Exception {
        withdraw("alice", "wd-2", "{\"amount\": 40.00, \"otp\": \"123456\"}").andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Transaction txn = transactions.findAll().stream()
                    .filter(t -> t.getWalletId().equals(wallet.getId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(txn.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
            assertThat(wallets.findById(wallet.getId()).orElseThrow().getBalance())
                    .isEqualByComparingTo(new BigDecimal("60.00")); // stays debited on success
        });
    }

    @Test
    void insufficientBalanceRejected() throws Exception {
        withdraw("alice", "wd-3", "{\"amount\": 150.00, \"otp\": \"123456\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));
        assertThat(wallets.findById(wallet.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("100.00")); // untouched
    }

    /**
     * The scenario WalletService.withdraw's `balance - reserved` check exists to prevent: a
     * pending (unconfirmed) deposit's money must not be withdrawable. `reserved` here simulates
     * a $30 deposit still awaiting gateway confirmation -- only the remaining $70 is actually
     * available, even though raw balance is $100.
     */
    @Test
    void withdrawalBlockedByPendingDepositCannotExceedAvailableBalance() throws Exception {
        jdbc.update("UPDATE wallets SET reserved = 30.00 WHERE id = ?", wallet.getId());

        withdraw("alice", "wd-4", "{\"amount\": 80.00, \"otp\": \"123456\"}") // > available (100 - 30 = 70)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));
        assertThat(wallets.findById(wallet.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("100.00")); // untouched
    }

    @Test
    void withdrawalAllowedUpToAvailableBalanceWithPendingDeposit() throws Exception {
        jdbc.update("UPDATE wallets SET reserved = 30.00 WHERE id = ?", wallet.getId());

        withdraw("alice", "wd-5", "{\"amount\": 70.00, \"otp\": \"123456\"}") // == available (100 - 30)
                .andExpect(status().isOk());
        assertThat(wallets.findById(wallet.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("30.00")); // the reserved $30 is still intact
    }

    @Test
    void invalidOtpRejected() throws Exception {
        withdraw("alice", "wd-6", "{\"amount\": 40.00, \"otp\": \"000000\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OTP"));
        assertThat(wallets.findById(wallet.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("100.00")); // untouched
    }

    @Test
    void inactiveWalletRejected() throws Exception {
        jdbc.update("UPDATE wallets SET status = 'SUSPENDED' WHERE id = ?", wallet.getId());
        withdraw("alice", "wd-7", "{\"amount\": 40.00, \"otp\": \"123456\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_ACTIVE"));
    }

    @Test
    void withdrawalFromAnotherUsersWalletReturns404() throws Exception {
        withdraw("mallory", "wd-8", "{\"amount\": 40.00, \"otp\": \"123456\"}").andExpect(status().isNotFound());
    }

    @Test
    void withdrawalWithoutAuthReturns401() throws Exception {
        mvc.perform(post("/wallets/" + wallet.getId() + "/withdrawals")
                        .header("Idempotency-Key", "wd-9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 40.00, \"otp\": \"123456\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingIdempotencyKeyRejected() throws Exception {
        mvc.perform(post("/wallets/" + wallet.getId() + "/withdrawals")
                        .with(as("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 40.00, \"otp\": \"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    /**
     * The skill's explicit concurrency requirement: balance = $100, two concurrent $80
     * withdrawals -- only one may succeed, the wallet must never go negative. The row lock in
     * WalletRepository.findByIdForUpdate is what's actually being tested here, not application
     * logic -- a check-then-act without it would let both through.
     *
     * Deliberately two DIFFERENT idempotency keys: this models two genuinely separate withdrawal
     * attempts racing, not one attempt being retried (that's concurrentDuplicateRequestsBelow).
     */
    @Test
    void concurrentWithdrawalsCannotOverdraw() throws Exception {
        int n = 2;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        var results = new java.util.ArrayList<Future<Integer>>();
        for (int i = 0; i < n; i++) {
            String key = "wd-concurrent-" + i;
            results.add(pool.submit(() -> {
                start.await();
                return withdraw("alice", key, "{\"amount\": 80.00, \"otp\": \"123456\"}")
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        int okCount = 0;
        for (var f : results) {
            int status = f.get();
            if (status == 200) okCount++;
            else assertThat(status).isEqualTo(422); // INSUFFICIENT_BALANCE for the loser
        }
        pool.shutdown();

        assertThat(okCount).isEqualTo(1);
        BigDecimal finalBalance = wallets.findById(wallet.getId()).orElseThrow().getBalance();
        assertThat(finalBalance).isEqualByComparingTo(new BigDecimal("20.00")); // never negative
    }

    // --- Idempotency (3c), same pattern as DepositApiTest ---

    @Test
    void duplicateIdempotencyKeySameRequestReturnsOriginalResponseAndDebitsOnce() throws Exception {
        String first = withdraw("alice", "wd-retry", "{\"amount\": 40.00, \"otp\": \"123456\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String second = withdraw("alice", "wd-retry", "{\"amount\": 40.00, \"otp\": \"123456\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(wallets.findById(wallet.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("60.00")); // not 20 -- only debited once
        assertThat(transactions.findAll().stream().filter(t -> t.getWalletId().equals(wallet.getId())).count())
                .isEqualTo(1);
    }

    @Test
    void sameIdempotencyKeyDifferentRequestRejected() throws Exception {
        withdraw("alice", "wd-mismatch", "{\"amount\": 40.00, \"otp\": \"123456\"}").andExpect(status().isOk());
        withdraw("alice", "wd-mismatch", "{\"amount\": 50.00, \"otp\": \"123456\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(wallets.findById(wallet.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("60.00")); // only the first request applied
    }

    @Test
    void concurrentDuplicateRequestsDebitOnlyOnce() throws Exception {
        int n = 8;
        var pool = Executors.newFixedThreadPool(n);
        var start = new CountDownLatch(1);
        var results = new java.util.ArrayList<Future<Integer>>();
        for (int i = 0; i < n; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return withdraw("alice", "wd-concurrent-dup", "{\"amount\": 40.00, \"otp\": \"123456\"}")
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        int okCount = 0;
        for (var f : results) {
            int status = f.get();
            if (status == 200) okCount++;
            else assertThat(status).isEqualTo(409); // IN_PROGRESS if it raced
        }
        pool.shutdown();

        assertThat(okCount).isGreaterThanOrEqualTo(1);
        assertThat(wallets.findById(wallet.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("60.00")); // debited once, not up to 8 times
        assertThat(transactions.findAll().stream().filter(t -> t.getWalletId().equals(wallet.getId())).count())
                .isEqualTo(1);
    }
}
