package com.example.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
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
 * DEFINED_PORT (not MOCK, unlike AbstractIntegrationTest's other tests): PaymentProcessor makes a
 * genuine outbound RestClient call back into this same app, so a real listener has to be up for
 * it to connect to. server.port/wallet.payment.base-url in test application.yml both point at
 * 9090, deliberately not 8080, so this doesn't collide with a `mvn spring-boot:run` left running
 * locally. Own Testcontainers container since the web environment differs from AbstractIntegrationTest.
 *
 * "Gateway failure" for processPaymentAsync's own branching is covered by PaymentProcessorTest
 * (a mocked unit test) instead of here — the current mock gateway (MockPaymentController) always
 * succeeds, so there's no way yet to make a *real* call fail on demand; that lands with 3d.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@AutoConfigureMockMvc
@Testcontainers
class DepositApiTest {
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
        // idempotency_keys first -- it references users, same as transactions references wallets.
        jdbc.update("DELETE FROM idempotency_keys");
        jdbc.update("DELETE FROM transactions");
        jdbc.update("DELETE FROM wallets");
        jdbc.update("DELETE FROM users");
        wallet = walletService.create("alice");
    }

    private RequestPostProcessor as(String user) {
        return jwt().jwt(j -> j.subject(user));
    }

    private org.springframework.test.web.servlet.ResultActions deposit(String user, String idempotencyKey, String body) throws Exception {
        return mvc.perform(post("/wallets/" + wallet.getId() + "/deposits")
                .with(as(user))
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void depositCreditsBalanceImmediately() throws Exception {
        deposit("alice", "dep-1", "{\"amount\": 100.00}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(wallet.getId().toString()))
                .andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    void invalidAmountRejected() throws Exception {
        deposit("alice", "dep-2", "{\"amount\": -5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(wallets.findById(wallet.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void depositIntoAnotherUsersWalletReturns404() throws Exception {
        deposit("mallory", "dep-3", "{\"amount\": 100.00}").andExpect(status().isNotFound());
    }

    @Test
    void depositWithoutAuthReturns401() throws Exception {
        mvc.perform(post("/wallets/" + wallet.getId() + "/deposits")
                        .header("Idempotency-Key", "dep-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100.00}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingIdempotencyKeyRejected() throws Exception {
        mvc.perform(post("/wallets/" + wallet.getId() + "/deposits")
                        .with(as("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    /**
     * End-to-end through the real HTTP hop into MockPaymentController. Also asserts `reserved`
     * drops back to 0 once confirmed -- currently nothing releases it on the success path
     * (only revertDeposit touches it, and that's the failure path), so this is expected to fail
     * until that's added.
     */
    @Test
    void depositEventuallySettlesCompletedAndReleasesReserved() throws Exception {
        deposit("alice", "dep-5", "{\"amount\": 100.00}").andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Transaction txn = transactions.findAll().stream()
                    .filter(t -> t.getWalletId().equals(wallet.getId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(txn.getStatus()).isEqualTo(TransactionStatus.COMPLETED);

            Wallet settled = wallets.findById(wallet.getId()).orElseThrow();
            assertThat(settled.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(settled.getReserved()).isEqualByComparingTo(BigDecimal.ZERO);
        });
    }

    // --- Iteration 3b: idempotency ---

    @Test
    void duplicateIdempotencyKeySameRequestReturnsOriginalResponseAndMovesMoneyOnce() throws Exception {
        String first = deposit("alice", "dep-retry", "{\"amount\": 100.00}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String second = deposit("alice", "dep-retry", "{\"amount\": 100.00}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(wallets.findById(wallet.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("100.00")); // not 200 -- only credited once
        assertThat(transactions.findAll().stream().filter(t -> t.getWalletId().equals(wallet.getId())).count())
                .isEqualTo(1);
    }

    @Test
    void sameIdempotencyKeyDifferentRequestRejected() throws Exception {
        deposit("alice", "dep-mismatch", "{\"amount\": 50.00}").andExpect(status().isOk());
        deposit("alice", "dep-mismatch", "{\"amount\": 75.00}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        // The first (only valid) request's amount is what actually applied.
        assertThat(wallets.findById(wallet.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void concurrentDuplicateRequestsMoveMoneyOnlyOnce() throws Exception {
        int n = 8;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(n);
        var start = new java.util.concurrent.CountDownLatch(1);
        var results = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
        for (int i = 0; i < n; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return deposit("alice", "dep-concurrent", "{\"amount\": 100.00}")
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        int okCount = 0;
        for (var f : results) {
            int status = f.get();
            if (status == 200) okCount++;
            else assertThat(status).isEqualTo(409); // IN_PROGRESS if it raced, or REUSED never (same body)
        }
        pool.shutdown();

        // The key invariant: however many callers got a 200 back (one winner, or more if some
        // arrived after the winner had already completed and got a valid replay), the wallet
        // only actually moved once.
        assertThat(okCount).isGreaterThanOrEqualTo(1);
        assertThat(wallets.findById(wallet.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(transactions.findAll().stream().filter(t -> t.getWalletId().equals(wallet.getId())).count())
                .isEqualTo(1);
    }
}
