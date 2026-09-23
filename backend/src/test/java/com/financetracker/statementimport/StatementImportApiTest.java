package com.financetracker.statementimport;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class StatementImportApiTest {

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
    void previewConfirmDuplicateProtectionAndIsolationWork() {
        Long version = jdbc.queryForObject(
                "select max(installed_rank) from flyway_schema_history where success = true", Long.class);
        assertThat(version).isEqualTo(8L);

        String ada = token("ada-import@example.com", "Ada");
        String grace = token("grace-import@example.com", "Grace");
        long adaAccount = createAccount(ada, "HDFC Savings", "1234567890");
        createAccount(grace, "Grace Bank", "5555");

        ResponseEntity<JsonNode> preview = uploadCsv(ada, "statement.csv", """
                Date,Narration,Debit,Credit,Reference,Account Number,Account Name
                2026-03-01,Coffee,120.50,,UPI-1,1234567890,HDFC Savings
                2026-03-02,Salary,,2000.00,NEFT-9,1234567890,HDFC Savings
                2026-03-03,,50.00,,,1234567890,HDFC Savings
                """);
        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode previewBody = preview.getBody();
        assertThat(previewBody.get("detectedAccount").get("suggestedAccountId").asLong()).isEqualTo(adaAccount);
        assertThat(previewBody.get("rows")).hasSize(2);
        assertThat(previewBody.get("skippedRows")).hasSize(1);
        assertThat(previewBody.get("summary").get("incomeTotal").asText()).isEqualTo("2000.00");
        assertThat(previewBody.get("summary").get("expenseTotal").asText()).isEqualTo("120.50");

        String sessionId = previewBody.get("sessionId").asText();
        List<String> fingerprints = List.of(
                previewBody.get("rows").get(0).get("fingerprint").asText(),
                previewBody.get("rows").get(1).get("fingerprint").asText());

        ResponseEntity<JsonNode> unauthorizedConfirm = jsonExchange(
                "/api/statement-imports/" + sessionId + "/confirm",
                HttpMethod.POST,
                grace,
                Map.of("accountId", adaAccount, "rowFingerprints", fingerprints));
        assertThat(unauthorizedConfirm.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<JsonNode> confirm = jsonExchange(
                "/api/statement-imports/" + sessionId + "/confirm",
                HttpMethod.POST,
                ada,
                Map.of("accountId", adaAccount, "rowFingerprints", fingerprints));
        assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirm.getBody().get("importedCount").asInt()).isEqualTo(2);
        assertThat(confirm.getBody().get("duplicateCount").asInt()).isZero();
        assertThat(confirm.getBody().get("accountBalance").asText()).isEqualTo("6879.50");

        JsonNode account = jsonExchange("/api/accounts/" + adaAccount, HttpMethod.GET, ada, null).getBody().get("account");
        assertThat(account.get("currentBalance").asText()).isEqualTo("6879.50");

        ResponseEntity<JsonNode> duplicateConfirm = jsonExchange(
                "/api/statement-imports/" + sessionId + "/confirm",
                HttpMethod.POST,
                ada,
                Map.of("accountId", adaAccount, "rowFingerprints", fingerprints));
        assertThat(duplicateConfirm.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(duplicateConfirm.getBody().get("importedCount").asInt()).isZero();
        assertThat(duplicateConfirm.getBody().get("duplicateCount").asInt()).isEqualTo(2);

        ResponseEntity<JsonNode> transactions = jsonExchange("/api/transactions?accountId=" + adaAccount, HttpMethod.GET, ada, null);
        assertThat(transactions.getBody().get("totalElements").asInt()).isEqualTo(2);
    }

    @Test
    void rejectsUnsupportedFilesAndMalformedCsvUploads() {
        String token = token("ada-invalid-import@example.com", "Ada Invalid");

        ResponseEntity<JsonNode> wrongType = uploadCsv(token, "statement.txt", "Date,Description,Amount\n2026-03-01,Salary,1000");
        assertThat(wrongType.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(wrongType.getBody().get("message").asText()).contains("Only CSV bank statements are supported");

        ResponseEntity<JsonNode> malformed = uploadCsv(token, "statement.csv", "Date,Description,Debit,Credit\n2026-03-01,Bad,10,20");
        assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(malformed.getBody().get("message").asText()).contains("both debit and credit are populated");
    }

    private String token(String email, String name) {
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/auth/register", Map.of(
                "email", email,
                "password", "password123",
                "name", name), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("token").asText();
    }

    private long createAccount(String token, String name, String accountNumber) {
        ResponseEntity<JsonNode> response = jsonExchange("/api/accounts", HttpMethod.POST, token, Map.of(
                "name", name,
                "type", "BANK",
                "openingBalance", "5000.00",
                "currency", "INR",
                "accountNumber", accountNumber));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").asLong();
    }

    private ResponseEntity<JsonNode> uploadCsv(String token, String filename, String content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new NamedByteArrayResource(filename, content.getBytes(StandardCharsets.UTF_8)));
        return rest.exchange("/api/statement-imports/preview", HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> jsonExchange(String path, HttpMethod method, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(String filename, byte[] byteArray) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
