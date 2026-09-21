package com.example.wallet;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.wallet.entity.Wallet;
import com.example.wallet.entity.WalletStatus;
import com.example.wallet.repository.WalletRepository;
import com.example.wallet.service.WalletService;

class WalletApiTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired WalletRepository wallets;
    @Autowired WalletService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM transactions");
        jdbc.update("DELETE FROM wallets");
        jdbc.update("DELETE FROM users");
    }

    private RequestPostProcessor as(String user) {
        return jwt().jwt(j -> j.subject(user));
    }

    @Test
    void createsWallet() throws Exception {
        mvc.perform(post("/wallets").with(as("alice")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").exists());
        List<Wallet> all = wallets.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getUserId()).isEqualTo("alice");
        assertThat(all.get(0).getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(all.get(0).getStatus()).isEqualTo(WalletStatus.ACTIVE);
    }

    @Test
    void duplicateCreationReturns409() throws Exception {
        mvc.perform(post("/wallets").with(as("alice"))).andExpect(status().isCreated());
        mvc.perform(post("/wallets").with(as("alice")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WALLET_ALREADY_EXISTS"));
        assertThat(wallets.count()).isEqualTo(1);
    }

    @Test
    void concurrentCreationCreatesOnlyOneWallet() throws Exception {
        int n = 8;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    service.create("bob");
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }));
        }
        start.countDown();
        int ok = 0;
        for (Future<Boolean> f : results) if (f.get()) ok++;
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
        assertThat(wallets.count()).isEqualTo(1);
    }

    @Test
    void createWithoutAuthReturns401() throws Exception {
        mvc.perform(post("/wallets")).andExpect(status().isUnauthorized());
    }

    @Test
    void retrievesOwnWallet() throws Exception {
        Wallet w = service.create("alice");
        mvc.perform(get("/wallets/" + w.getId()).with(as("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(w.getId().toString()))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void missingWalletReturns404() throws Exception {
        mvc.perform(get("/wallets/" + UUID.randomUUID()).with(as("alice")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
    }

    @Test
    void otherUsersWalletReturns404() throws Exception {
        Wallet w = service.create("alice");
        mvc.perform(get("/wallets/" + w.getId()).with(as("mallory")))
                .andExpect(status().isNotFound());
    }

    @Test
    void retrieveWithoutAuthReturns401() throws Exception {
        mvc.perform(get("/wallets/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }
}
