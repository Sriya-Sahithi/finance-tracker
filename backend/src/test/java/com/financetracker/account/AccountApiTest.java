package com.financetracker.account;

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
class AccountApiTest {

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
    void migrationAndApiPersistNormalizedAccountNumber() {
        Map<String, Object> metadata = jdbc.queryForMap("""
                select is_nullable, character_maximum_length
                from information_schema.columns
                where table_name = 'accounts' and column_name = 'account_number'
                """);
        assertThat(metadata.get("is_nullable")).isEqualTo("YES");
        assertThat(((Number) metadata.get("character_maximum_length")).intValue()).isEqualTo(34);

        String token = token("ada-account@example.com", "Ada");
        ResponseEntity<JsonNode> created = exchange("/api/accounts", HttpMethod.POST, token, Map.of(
                "name", "HDFC Savings",
                "type", "BANK",
                "accountNumber", " 12 34-abcd-5678 ",
                "openingBalance", "10000.00",
                "currency", "INR"));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long accountId = created.getBody().get("id").asLong();
        assertThat(created.getBody().get("accountNumber").asText()).isEqualTo("1234ABCD5678");

        JsonNode listed = exchange("/api/accounts", HttpMethod.GET, token, null).getBody();
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).get("accountNumber").asText()).isEqualTo("1234ABCD5678");

        JsonNode detail = exchange("/api/accounts/" + accountId, HttpMethod.GET, token, null).getBody().get("account");
        assertThat(detail.get("accountNumber").asText()).isEqualTo("1234ABCD5678");
        assertThat(jdbc.queryForObject("select account_number from accounts where id = ?", String.class, accountId))
                .isEqualTo("1234ABCD5678");
    }

    @Test
    void updateSupportsOmissionReplacementAndClearing() {
        String token = token("grace-account@example.com", "Grace");
        long accountId = exchange("/api/accounts", HttpMethod.POST, token, Map.of(
                "name", "ICICI Savings",
                "type", "BANK",
                "accountNumber", "1234 5678",
                "openingBalance", "5000.00",
                "currency", "INR")).getBody().get("id").asLong();

        ResponseEntity<JsonNode> unchanged = exchange("/api/accounts/" + accountId, HttpMethod.PUT, token, Map.of(
                "name", "ICICI Primary",
                "type", "BANK",
                "openingBalance", "5200.00",
                "currency", "INR"));
        assertThat(unchanged.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unchanged.getBody().get("accountNumber").asText()).isEqualTo("12345678");

        ResponseEntity<JsonNode> replaced = exchange("/api/accounts/" + accountId, HttpMethod.PUT, token, Map.of(
                "name", "ICICI Primary",
                "type", "BANK",
                "accountNumber", "ZZ99-8888",
                "openingBalance", "5200.00",
                "currency", "INR"));
        assertThat(replaced.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replaced.getBody().get("accountNumber").asText()).isEqualTo("ZZ998888");

        ResponseEntity<JsonNode> cleared = exchange("/api/accounts/" + accountId, HttpMethod.PUT, token, Map.of(
                "name", "ICICI Primary",
                "type", "BANK",
                "accountNumber", "   ",
                "openingBalance", "5200.00",
                "currency", "INR"));
        assertThat(cleared.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cleared.getBody().get("accountNumber").isNull()).isTrue();
    }

    @Test
    void createWithoutAccountNumberRemainsBackwardCompatible() {
        String token = token("cash-account@example.com", "Cash User");
        ResponseEntity<JsonNode> created = exchange("/api/accounts", HttpMethod.POST, token, Map.of(
                "name", "Wallet",
                "type", "CASH",
                "openingBalance", "250.00",
                "currency", "INR"));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("accountNumber").isNull()).isTrue();

        long accountId = created.getBody().get("id").asLong();
        ResponseEntity<JsonNode> converted = exchange("/api/accounts/" + accountId, HttpMethod.PUT, token, Map.of(
                "name", "Wallet Bank",
                "type", "BANK",
                "openingBalance", "250.00",
                "currency", "INR"));
        assertThat(converted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(converted.getBody().get("accountNumber").isNull()).isTrue();
    }

    private String token(String email, String name) {
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/auth/register", Map.of(
                "email", email,
                "password", "password123",
                "name", name), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("token").asText();
    }

    private ResponseEntity<JsonNode> exchange(String path, HttpMethod method, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }
}
