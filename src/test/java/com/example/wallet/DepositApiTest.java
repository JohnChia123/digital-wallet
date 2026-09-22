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
        jdbc.update("DELETE FROM transactions");
        jdbc.update("DELETE FROM wallets");
        jdbc.update("DELETE FROM users");
        wallet = walletService.create("alice");
    }

    private RequestPostProcessor as(String user) {
        return jwt().jwt(j -> j.subject(user));
    }

    private org.springframework.test.web.servlet.ResultActions deposit(String user, String body) throws Exception {
        return mvc.perform(post("/wallets/" + wallet.getId() + "/deposits")
                .with(as(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void depositCreditsBalanceImmediately() throws Exception {
        deposit("alice", "{\"amount\": 100.00}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(wallet.getId().toString()))
                .andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    void invalidAmountRejected() throws Exception {
        deposit("alice", "{\"amount\": -5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(wallets.findById(wallet.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void depositIntoAnotherUsersWalletReturns404() throws Exception {
        deposit("mallory", "{\"amount\": 100.00}").andExpect(status().isNotFound());
    }

    @Test
    void depositWithoutAuthReturns401() throws Exception {
        mvc.perform(post("/wallets/" + wallet.getId() + "/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100.00}"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * End-to-end through the real HTTP hop into MockPaymentController. Also asserts `reserved`
     * drops back to 0 once confirmed -- currently nothing releases it on the success path
     * (only revertDeposit touches it, and that's the failure path), so this is expected to fail
     * until that's added.
     */
    @Test
    void depositEventuallySettlesCompletedAndReleasesReserved() throws Exception {
        deposit("alice", "{\"amount\": 100.00}").andExpect(status().isOk());

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
}
