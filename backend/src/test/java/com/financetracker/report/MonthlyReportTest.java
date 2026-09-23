package com.financetracker.report;

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
class MonthlyReportTest {

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
    void cashFlowExcludesTransfersAndIsolatesUsers() {
        String ada = token("ada-report@example.com");
        String grace = token("grace-report@example.com");
        long salary = category(ada, "Salary", "INCOME");
        long food = category(ada, "Food & Dining", "EXPENSE");
        long bank = account(ada, "Bank");
        long cash = account(ada, "Cash");

        post(ada, "/api/transactions", Map.of(
                "accountId", bank, "categoryId", salary, "type", "INCOME",
                "amount", "8000.00", "transactionDate", "2026-03-01", "description", "Pay"));
        post(ada, "/api/transactions", Map.of(
                "accountId", bank, "categoryId", food, "type", "EXPENSE",
                "amount", "1200.00", "transactionDate", "2026-03-12", "description", "Food"));
        post(ada, "/api/transactions", Map.of(
                "accountId", bank, "transferAccountId", cash, "type", "TRANSFER",
                "amount", "500.00", "transactionDate", "2026-03-15", "description", "Move"));
        post(ada, "/api/budgets", Map.of(
                "categoryId", food, "year", 2026, "month", 3, "amount", "1000.00"));
        post(ada, "/api/loans", Map.of(
                "name", "Auto", "loanType", "AUTO", "principalAmount", "100000.00",
                "annualInterestRate", "10", "tenureMonths", 12, "startDate", "2026-01-05",
                "firstPaymentDate", "2026-02-05", "paymentDueDay", 5));

        JsonNode report = exchange("/api/reports/monthly?year=2026&month=3", HttpMethod.GET, ada, null).getBody();
        JsonNode march = report.get("cashFlow").get(2);
        assertThat(march.get("income").asText()).isEqualTo("8000.00");
        assertThat(march.get("expenses").asText()).isEqualTo("1200.00");
        assertThat(march.get("savings").asText()).isEqualTo("6800.00");
        assertThat(report.get("monthlySpending").get(2).get("amount").asText()).isEqualTo("1200.00");
        assertThat(report.get("expenseByCategory")).hasSize(1);
        assertThat(report.get("expenseByCategory").get(0).get("categoryName").asText()).isEqualTo("Food & Dining");
        assertThat(report.get("budgetUtilization").get(0).get("overBudget").asBoolean()).isTrue();
        assertThat(report.get("loanBalanceOverTime")).hasSize(12);
        assertThat(report.get("loanBalanceOverTime").get(2).get("amount").asText()).isEqualTo("100000.00");
        assertThat(report.get("interestVsPrincipal")).hasSize(12);

        JsonNode other = exchange("/api/reports/monthly?year=2026&month=3", HttpMethod.GET, grace, null).getBody();
        assertThat(other.get("cashFlow").get(2).get("income").asText()).isEqualTo("0.00");
        assertThat(other.get("loanBalanceOverTime").get(2).get("amount").asText()).isEqualTo("0.00");
    }

    private void post(String token, String path, Map<String, Object> body) {
        ResponseEntity<JsonNode> response = exchange(path, HttpMethod.POST, token, body);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    private long account(String token, String name) {
        ResponseEntity<JsonNode> response = exchange("/api/accounts", HttpMethod.POST, token, Map.of(
                "name", name, "type", "BANK", "openingBalance", "1000.00", "currency", "INR"));
        return response.getBody().get("id").asLong();
    }

    private long category(String token, String name, String type) {
        for (JsonNode category : exchange("/api/categories", HttpMethod.GET, token, null).getBody()) {
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
