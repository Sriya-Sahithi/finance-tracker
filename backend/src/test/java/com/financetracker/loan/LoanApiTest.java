package com.financetracker.loan;

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
class LoanApiTest {

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
    void schedulePaymentsPrepaymentAndIsolation() {
        String ada = token("ada-loan@example.com");
        String grace = token("grace-loan@example.com");
        long accountId = account(ada);

        ResponseEntity<JsonNode> created = exchange("/api/loans", HttpMethod.POST, ada, loanBody());
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode loan = created.getBody();
        long loanId = loan.get("id").asLong();
        assertThat(loan.get("emiAmount").asText()).isEqualTo("8791.59");
        assertThat(loan.get("outstandingPrincipal").asText()).isEqualTo("100000.00");
        assertThat(loan.has("password")).isFalse();

        JsonNode schedule = exchange("/api/loans/" + loanId + "/schedule", HttpMethod.GET, ada, null).getBody();
        assertThat(schedule.get("schedule")).hasSize(12);
        JsonNode last = schedule.get("schedule").get(11);
        assertThat(last.get("closingPrincipal").asText()).isEqualTo("0.00");
        assertThat(last.get("kind").asText()).isEqualTo("PROJECTED");

        assertThat(exchange("/api/loans/" + loanId, HttpMethod.GET, grace, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange("/api/loans/" + loanId + "/schedule", HttpMethod.GET, grace, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<JsonNode> prepay = exchange("/api/loans/" + loanId + "/prepayment", HttpMethod.POST, ada, Map.of(
                "paymentDate", "2026-02-05",
                "extraPrincipalAmount", "10000.00",
                "accountId", accountId,
                "notes", "Extra"));
        assertThat(prepay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode analysis = prepay.getBody();
        assertThat(analysis.get("strategy").asText()).isEqualTo("REDUCE_TENURE");
        assertThat(new java.math.BigDecimal(analysis.get("interestSaved").asText())).isGreaterThan(java.math.BigDecimal.ZERO);
        assertThat(analysis.get("emisReduced").asInt()).isGreaterThan(0);
        assertThat(analysis.get("newOutstanding").asText()).isEqualTo(analysis.get("payment").get("remainingPrincipal").asText());
        assertThat(analysis.get("payment").get("extraPrincipalAmount").asText()).isEqualTo("10000.00");

        JsonNode balance = exchange("/api/accounts/" + accountId, HttpMethod.GET, ada, null).getBody().get("account");
        java.math.BigDecimal expected = new java.math.BigDecimal("20000.00")
                .subtract(new java.math.BigDecimal(analysis.get("payment").get("totalAmount").asText()));
        assertThat(balance.get("currentBalance").asText()).isEqualTo(expected.setScale(2).toPlainString());

        JsonNode updated = exchange("/api/loans/" + loanId, HttpMethod.GET, ada, null).getBody();
        assertThat(updated.get("outstandingPrincipal").asText()).isEqualTo(analysis.get("newOutstanding").asText());

        JsonNode midDashboard = exchange("/api/dashboard?year=2026&month=2", HttpMethod.GET, ada, null).getBody();
        assertThat(midDashboard.get("loans").get("activeLoanCount").asInt()).isEqualTo(1);
        assertThat(midDashboard.get("loans").get("totalEmiObligation").asText()).isEqualTo("8791.59");
        assertThat(midDashboard.get("loans").get("upcomingPayments")).hasSize(1);

        JsonNode after = exchange("/api/loans/" + loanId + "/schedule", HttpMethod.GET, ada, null).getBody().get("schedule");
        assertThat(after.get(0).get("kind").asText()).isEqualTo("ACTUAL");
        assertThat(after.get(after.size() - 1).get("closingPrincipal").asText()).isEqualTo("0.00");

        ResponseEntity<JsonNode> payoff = exchange("/api/loans/" + loanId + "/payments", HttpMethod.POST, ada, Map.of(
                "paymentDate", "2026-03-05",
                "extraPrincipalAmount", "100000.00",
                "accountId", accountId));
        assertThat(payoff.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(payoff.getBody().get("remainingPrincipal").asText()).isEqualTo("0.00");

        JsonNode dashboard = exchange("/api/dashboard?year=2026&month=2", HttpMethod.GET, ada, null).getBody();
        assertThat(dashboard.get("loans").get("activeLoanCount").asInt()).isZero();
        assertThat(dashboard.get("loans").get("totalOutstanding").asText()).isEqualTo("0.00");

        assertThat(exchange("/api/loans/" + loanId + "/payments", HttpMethod.GET, grace, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private Map<String, Object> loanBody() {
        return Map.of(
                "name", "Home loan",
                "loanType", "HOME",
                "principalAmount", "100000.00",
                "annualInterestRate", "10.0000",
                "tenureMonths", 12,
                "startDate", "2026-01-05",
                "firstPaymentDate", "2026-02-05",
                "paymentDueDay", 5);
    }

    private long account(String token) {
        ResponseEntity<JsonNode> response = exchange("/api/accounts", HttpMethod.POST, token, Map.of(
                "name", "Salary", "type", "BANK", "openingBalance", "20000.00", "currency", "INR"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").asLong();
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
