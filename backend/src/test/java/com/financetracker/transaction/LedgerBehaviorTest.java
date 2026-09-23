package com.financetracker.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class LedgerBehaviorTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void balancesTransfersFiltersAndOwnership() {
        Long version = jdbc.queryForObject(
                "select max(installed_rank) from flyway_schema_history where success = true", Long.class);
        assertThat(version).isEqualTo(4L);

        String ada = token("ada-ledger@example.com", "Ada");
        String grace = token("grace-ledger@example.com", "Grace");

        ResponseEntity<JsonNode> categories = exchange("/api/categories", HttpMethod.GET, ada, null);
        assertThat(categories.getBody()).hasSizeGreaterThan(5);
        long salary = categoryId(categories.getBody(), "Salary", "INCOME");
        long food = categoryId(categories.getBody(), "Food & Dining", "EXPENSE");

        long bank = createAccount(ada, "HDFC", "BANK", "10000.00");
        long cash = createAccount(ada, "Wallet", "CASH", "500.00");
        long graceBank = createAccount(grace, "SBI", "BANK", "50.00");

        ResponseEntity<JsonNode> hidden = exchange("/api/accounts/" + bank, HttpMethod.GET, grace, null);
        assertThat(hidden.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<JsonNode> income = exchange("/api/transactions", HttpMethod.POST, ada, Map.of(
                "accountId", bank,
                "categoryId", salary,
                "type", "INCOME",
                "amount", "2000.00",
                "transactionDate", "2026-03-05",
                "description", "March salary"));
        assertThat(income.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<JsonNode> expense = exchange("/api/transactions", HttpMethod.POST, ada, Map.of(
                "accountId", bank,
                "categoryId", food,
                "type", "EXPENSE",
                "amount", "180.50",
                "transactionDate", "2026-03-06",
                "description", "Lunch"));
        assertThat(expense.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long expenseId = expense.getBody().get("id").asLong();

        ResponseEntity<JsonNode> transfer = exchange("/api/transactions", HttpMethod.POST, ada, Map.of(
                "accountId", bank,
                "transferAccountId", cash,
                "type", "TRANSFER",
                "amount", "300.00",
                "transactionDate", "2026-03-07",
                "description", "ATM"));
        assertThat(transfer.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<JsonNode> otherUserTx = exchange("/api/transactions", HttpMethod.POST, grace, Map.of(
                "accountId", bank,
                "categoryId", salary,
                "type", "INCOME",
                "amount", "10.00",
                "transactionDate", "2026-03-08",
                "description", "Stolen"));
        assertThat(otherUserTx.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        JsonNode bankDetail = exchange("/api/accounts/" + bank, HttpMethod.GET, ada, null).getBody().get("account");
        JsonNode cashDetail = exchange("/api/accounts/" + cash, HttpMethod.GET, ada, null).getBody().get("account");
        assertThat(bankDetail.get("currentBalance").asText()).isEqualTo("11519.50");
        assertThat(cashDetail.get("currentBalance").asText()).isEqualTo("800.00");

        ResponseEntity<JsonNode> page = exchange(
                "/api/transactions?type=EXPENSE&search=lunch&from=2026-03-01&to=2026-03-31",
                HttpMethod.GET, ada, null);
        assertThat(page.getBody().get("totalElements").asInt()).isEqualTo(1);
        assertThat(page.getBody().get("content").get(0).get("id").asLong()).isEqualTo(expenseId);

        ResponseEntity<JsonNode> graceList = exchange("/api/transactions", HttpMethod.GET, grace, null);
        assertThat(graceList.getBody().get("totalElements").asInt()).isZero();
        ResponseEntity<JsonNode> graceRead = exchange("/api/transactions/" + expenseId, HttpMethod.GET, grace, null);
        assertThat(graceRead.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Void> deleted = exchangeVoid("/api/transactions/" + expenseId, HttpMethod.DELETE, ada);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        JsonNode afterDelete = exchange("/api/accounts/" + bank, HttpMethod.GET, ada, null).getBody().get("account");
        assertThat(afterDelete.get("currentBalance").asText()).isEqualTo("11700.00");

        ResponseEntity<JsonNode> badAmount = exchange("/api/transactions", HttpMethod.POST, ada, Map.of(
                "accountId", bank,
                "categoryId", food,
                "type", "EXPENSE",
                "amount", "0",
                "transactionDate", "2026-03-09"));
        assertThat(badAmount.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(exchange("/api/accounts/" + graceBank, HttpMethod.DELETE, ada, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private String token(String email, String name) {
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/auth/register", Map.of(
                "email", email,
                "password", "password123",
                "name", name), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("token").asText();
    }

    private long createAccount(String token, String name, String type, String opening) {
        ResponseEntity<JsonNode> response = exchange("/api/accounts", HttpMethod.POST, token, Map.of(
                "name", name,
                "type", type,
                "openingBalance", opening,
                "currency", "INR"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").asLong();
    }

    private long categoryId(JsonNode categories, String name, String type) {
        for (JsonNode category : categories) {
            if (name.equals(category.get("name").asText()) && type.equals(category.get("type").asText())) {
                return category.get("id").asLong();
            }
        }
        throw new AssertionError("Missing category " + name);
    }

    private ResponseEntity<JsonNode> exchange(String path, HttpMethod method, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private ResponseEntity<Void> exchangeVoid(String path, HttpMethod method, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(path, method, new HttpEntity<>(headers), Void.class);
    }
}
