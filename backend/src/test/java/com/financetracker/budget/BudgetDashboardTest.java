package com.financetracker.budget;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class BudgetDashboardTest {

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

    @Test
    void budgetsAreScopedByMonthAndUserAndTransfersAreNotExpenses() {
        String ada = token("ada-budget@example.com");
        String grace = token("grace-budget@example.com");
        long food = category(ada, "Food & Dining", "EXPENSE");
        long travel = category(ada, "Transport", "EXPENSE");
        long salary = category(ada, "Salary", "INCOME");
        long bank = account(ada, "Bank", "10000.00");
        long cash = account(ada, "Cash", "1000.00");

        assertThat(exchange("/api/budgets", HttpMethod.POST, ada, Map.of(
                "categoryId", salary, "year", 2026, "month", 3, "amount", "1000.00")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<JsonNode> foodBudget = exchange("/api/budgets", HttpMethod.POST, ada, Map.of(
                "categoryId", food, "year", 2026, "month", 3, "amount", "1000.00"));
        assertThat(foodBudget.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long foodBudgetId = foodBudget.getBody().get("id").asLong();

        exchange("/api/budgets", HttpMethod.POST, ada, Map.of(
                "categoryId", travel, "year", 2026, "month", 3, "amount", "500.00"));
        exchange("/api/budgets", HttpMethod.POST, ada, Map.of(
                "categoryId", food, "year", 2026, "month", 4, "amount", "2000.00"));

        txn(ada, bank, salary, "INCOME", "5000.00", "2026-03-01");
        txn(ada, bank, food, "EXPENSE", "400.00", "2026-03-10");
        txn(ada, bank, food, "EXPENSE", "700.00", "2026-03-20");
        txn(ada, bank, travel, "EXPENSE", "500.00", "2026-03-15");
        txn(ada, bank, food, "EXPENSE", "50.00", "2026-04-02");
        exchange("/api/transactions", HttpMethod.POST, ada, Map.of(
                "accountId", bank,
                "transferAccountId", cash,
                "type", "TRANSFER",
                "amount", "100.00",
                "transactionDate", "2026-03-18",
                "description", "Move cash"));

        JsonNode march = exchange("/api/budgets?year=2026&month=3", HttpMethod.GET, ada, null).getBody();
        JsonNode foodRow = find(march, "Food & Dining");
        JsonNode travelRow = find(march, "Transport");
        assertThat(foodRow.get("spent").asText()).isEqualTo("1100.00");
        assertThat(foodRow.get("remaining").asText()).isEqualTo("-100.00");
        assertThat(foodRow.get("usagePercent").asText()).isEqualTo("110.00");
        assertThat(foodRow.get("overBudget").asBoolean()).isTrue();
        assertThat(travelRow.get("spent").asText()).isEqualTo("500.00");
        assertThat(travelRow.get("remaining").asText()).isEqualTo("0.00");
        assertThat(travelRow.get("overBudget").asBoolean()).isFalse();

        JsonNode april = exchange("/api/budgets?year=2026&month=4", HttpMethod.GET, ada, null).getBody();
        assertThat(find(april, "Food & Dining").get("spent").asText()).isEqualTo("50.00");
        assertThat(find(april, "Food & Dining").get("overBudget").asBoolean()).isFalse();

        JsonNode dashboard = exchange("/api/dashboard?year=2026&month=3", HttpMethod.GET, ada, null).getBody();
        assertThat(dashboard.get("income").asText()).isEqualTo("5000.00");
        assertThat(dashboard.get("expenses").asText()).isEqualTo("1600.00");
        assertThat(dashboard.get("savings").asText()).isEqualTo("3400.00");
        assertThat(dashboard.get("totalBudget").asText()).isEqualTo("1500.00");
        assertThat(dashboard.get("budgetUsed").asText()).isEqualTo("1600.00");
        assertThat(dashboard.get("budgetRemaining").asText()).isEqualTo("-100.00");

        assertThat(exchange("/api/budgets/" + foodBudgetId, HttpMethod.GET, grace, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange("/api/budgets?year=2026&month=3", HttpMethod.GET, grace, null).getBody()).isEmpty();
    }

    private JsonNode find(JsonNode rows, String name) {
        for (JsonNode row : rows) {
            if (name.equals(row.get("categoryName").asText())) {
                return row;
            }
        }
        throw new AssertionError("Missing " + name);
    }

    private void txn(String token, long accountId, long categoryId, String type, String amount, String date) {
        ResponseEntity<JsonNode> response = exchange("/api/transactions", HttpMethod.POST, token, Map.of(
                "accountId", accountId,
                "categoryId", categoryId,
                "type", type,
                "amount", amount,
                "transactionDate", date,
                "description", type));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private long account(String token, String name, String opening) {
        ResponseEntity<JsonNode> response = exchange("/api/accounts", HttpMethod.POST, token, Map.of(
                "name", name, "type", "BANK", "openingBalance", opening, "currency", "INR"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").asLong();
    }

    private long category(String token, String name, String type) {
        ResponseEntity<JsonNode> categories = exchange("/api/categories", HttpMethod.GET, token, null);
        for (JsonNode category : categories.getBody()) {
            if (name.equals(category.get("name").asText()) && type.equals(category.get("type").asText())) {
                return category.get("id").asLong();
            }
        }
        throw new AssertionError(name);
    }

    private String token(String email) {
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/auth/register", Map.of(
                "email", email, "password", "password123", "name", "Tester"), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("token").asText();
    }

    private ResponseEntity<JsonNode> exchange(String path, HttpMethod method, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }
}
