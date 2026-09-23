package com.financetracker.auth;

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
import org.springframework.http.MediaType;
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
class AuthFlowTest {

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
    void healthIsPublicAndMigrationsRun() {
        ResponseEntity<String> health = rest.getForEntity("/actuator/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("UP");

        Long migrations = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = true", Long.class);
        assertThat(migrations).isEqualTo(1L);
    }

    @Test
    void registerLoginMeAndLogout() {
        ResponseEntity<JsonNode> registered = rest.postForEntity("/api/auth/register", Map.of(
                "email", "Ada@Example.com",
                "password", "password123",
                "name", "Ada Lovelace"), JsonNode.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = registered.getBody();
        assertThat(body.get("token").asText()).isNotBlank();
        assertThat(body.get("user").get("email").asText()).isEqualTo("ada@example.com");
        assertThat(body.get("user").has("password")).isFalse();
        assertThat(body.get("user").has("passwordHash")).isFalse();
        String token = body.get("token").asText();

        ResponseEntity<JsonNode> duplicate = rest.postForEntity("/api/auth/register", Map.of(
                "email", "ada@example.com",
                "password", "password123",
                "name", "Ada"), JsonNode.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<JsonNode> invalid = rest.postForEntity("/api/auth/login", Map.of(
                "email", "ada@example.com",
                "password", "wrong-password"), JsonNode.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<JsonNode> loggedIn = rest.postForEntity("/api/auth/login", Map.of(
                "email", "ada@example.com",
                "password", "password123"), JsonNode.class);
        assertThat(loggedIn.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> me = exchange("/api/users/me", HttpMethod.GET, token, null);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().get("name").asText()).isEqualTo("Ada Lovelace");

        ResponseEntity<JsonNode> anonymous = rest.getForEntity("/api/users/me", JsonNode.class);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Void> logout = rest.exchange(
                "/api/auth/logout", HttpMethod.POST, new HttpEntity<>(headers), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<JsonNode> afterLogout = exchange("/api/users/me", HttpMethod.GET, token, null);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsShortPasswordWithoutStackTrace() {
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/auth/register", Map.of(
                "email", "short@example.com",
                "password", "short",
                "name", "Short"), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("fieldErrors").toString()).contains("password");
        assertThat(response.getBody().has("trace")).isFalse();
        assertThat(response.getBody().toString()).doesNotContain("Exception");
    }

    private ResponseEntity<JsonNode> exchange(String path, HttpMethod method, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }
}
