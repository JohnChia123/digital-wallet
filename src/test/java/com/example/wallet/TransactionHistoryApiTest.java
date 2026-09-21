package com.example.wallet;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.wallet.entity.Transaction;
import com.example.wallet.entity.TransactionStatus;
import com.example.wallet.entity.TransactionType;
import com.example.wallet.entity.Wallet;
import com.example.wallet.repository.TransactionRepository;
import com.example.wallet.service.WalletService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class TransactionHistoryApiTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired WalletService walletService;
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

    private void seed(TransactionType type, String amount, TransactionStatus status, String createdAt) {
        transactions.saveAndFlush(new Transaction(wallet.getId(), type, new BigDecimal(amount),
                status, null, Instant.parse(createdAt)));
    }

    /** 5 transactions on distinct days, newest = Sep 20. */
    private void seedSample() {
        seed(TransactionType.DEPOSIT, "100.00", TransactionStatus.COMPLETED, "2026-09-10T10:00:00Z");
        seed(TransactionType.WITHDRAWAL, "20.00", TransactionStatus.COMPLETED, "2026-09-12T10:00:00Z");
        seed(TransactionType.WITHDRAWAL, "50.00", TransactionStatus.FAILED, "2026-09-15T10:00:00Z");
        seed(TransactionType.WITHDRAWAL, "500.00", TransactionStatus.COMPLETED, "2026-09-18T10:00:00Z");
        seed(TransactionType.DEPOSIT, "5.00", TransactionStatus.PENDING, "2026-09-20T10:00:00Z");
    }

    private ResultActions list(String query) throws Exception {
        return mvc.perform(get("/wallets/" + wallet.getId() + "/transactions" + query).with(as("alice")));
    }

    @Test
    void returnsTransactionsNewestFirst() throws Exception {
        seedSample();
        list("").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.items[0].amount").value(5.0))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.items[4].amount").value(100.0));
    }

    @Test
    void emptyHistory() throws Exception {
        list("").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void pagination() throws Exception {
        seedSample();
        list("?page=0&size=2").andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
        list("?page=2&size=2").andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].amount").value(100.0));
    }

    @Test
    void filterByType() throws Exception {
        seedSample();
        list("?type=DEPOSIT").andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void filterByStatus() throws Exception {
        seedSample();
        list("?status=FAILED").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].amount").value(50.0));
    }

    @Test
    void filterByDateRangeIsInclusive() throws Exception {
        seedSample();
        list("?from=2026-09-12&to=2026-09-18").andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void filterByMinAmount() throws Exception {
        seedSample();
        list("?minAmount=50").andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void filterByMaxAmount() throws Exception {
        seedSample();
        list("?maxAmount=50").andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void combinedFilters() throws Exception {
        seedSample();
        list("?type=WITHDRAWAL&status=COMPLETED&from=2026-09-01&to=2026-09-30&minAmount=10&maxAmount=500")
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void otherUsersWalletReturns404() throws Exception {
        seedSample();
        mvc.perform(get("/wallets/" + wallet.getId() + "/transactions").with(as("mallory")))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonexistentWalletReturns404() throws Exception {
        mvc.perform(get("/wallets/" + UUID.randomUUID() + "/transactions").with(as("alice")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
    }

    @Test
    void withoutAuthReturns401() throws Exception {
        mvc.perform(get("/wallets/" + wallet.getId() + "/transactions")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidQueryParametersReturn400() throws Exception {
        for (String q : new String[] {"?type=BANANA", "?status=NOPE", "?from=not-a-date", "?minAmount=abc",
                "?page=-1", "?size=0", "?size=101", "?page=x",
                "?from=2026-09-20&to=2026-09-01", "?minAmount=10&maxAmount=5"}) {
            list(q).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
    }
}
