package com.financetracker.statementimport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.financetracker.account.Account;
import com.financetracker.account.AccountService;
import com.financetracker.category.Category;
import com.financetracker.category.CategoryService;
import com.financetracker.category.CategoryType;
import com.financetracker.common.exception.BadRequestException;
import com.financetracker.common.exception.ResourceNotFoundException;
import com.financetracker.common.money.Money;
import com.financetracker.common.security.CurrentUserService;
import com.financetracker.statementimport.dto.StatementImportConfirmRequest;
import com.financetracker.statementimport.dto.StatementImportConfirmResponse;
import com.financetracker.statementimport.dto.StatementImportPreviewResponse;
import com.financetracker.transaction.AccountLedger;
import com.financetracker.transaction.Transaction;
import com.financetracker.transaction.TransactionRepository;
import com.financetracker.transaction.TransactionType;
import com.financetracker.user.User;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StatementImportService {

    private static final long MAX_FILE_SIZE_BYTES = 1024 * 1024;
    private static final TypeReference<List<StoredPreviewRow>> STORED_ROW_TYPE = new TypeReference<>() {
    };

    private final CsvStatementParser csvStatementParser;
    private final StatementImportSessionRepository sessionRepository;
    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    public StatementImportService(
            CsvStatementParser csvStatementParser,
            StatementImportSessionRepository sessionRepository,
            TransactionRepository transactionRepository,
            AccountService accountService,
            CategoryService categoryService,
            CurrentUserService currentUserService,
            ObjectMapper objectMapper
    ) {
        this.csvStatementParser = csvStatementParser;
        this.sessionRepository = sessionRepository;
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
        this.categoryService = categoryService;
        this.currentUserService = currentUserService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public StatementImportPreviewResponse preview(MultipartFile file) {
        validateFile(file);
        User user = currentUserService.require();
        ParsedStatementFile parsed = csvStatementParser.parse(readUtf8(file));
        SuggestedAccount suggested = suggestAccount(user.getId(), parsed.detectedAccountName(), parsed.detectedAccountNumber());
        List<StoredPreviewRow> storedRows = parsed.rows().stream()
                .map(row -> new StoredPreviewRow(
                        row.rowNumber(),
                        fingerprint(parsed.detectedAccountName(), parsed.detectedAccountNumber(), row),
                        row.transactionDate(),
                        row.description(),
                        row.reference(),
                        row.type(),
                        Money.scale(row.amount())))
                .toList();

        StatementImportSession session = new StatementImportSession();
        session.setId(UUID.randomUUID());
        session.setUser(user);
        session.setFileName(file.getOriginalFilename() == null ? "statement.csv" : file.getOriginalFilename().trim());
        session.setDetectedAccountName(parsed.detectedAccountName());
        session.setDetectedAccountNumber(parsed.detectedAccountNumber());
        session.setPreviewRowsJson(serializeRows(storedRows));
        sessionRepository.save(session);

        return toPreviewResponse(session.getId(), parsed, suggested, storedRows);
    }

    @Transactional
    public StatementImportConfirmResponse confirm(UUID sessionId, StatementImportConfirmRequest request) {
        User user = currentUserService.require();
        StatementImportSession session = sessionRepository.findByIdAndUserId(sessionId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Import session not found"));
        Account account = accountService.lockOwned(user.getId(), request.accountId());
        List<StoredPreviewRow> storedRows = readStoredRows(session.getPreviewRowsJson());
        if (storedRows.isEmpty()) {
            throw new BadRequestException("This import session has no valid rows to confirm");
        }

        LinkedHashSet<String> requestedFingerprints = new LinkedHashSet<>(request.rowFingerprints());
        Map<String, StoredPreviewRow> rowsByFingerprint = new LinkedHashMap<>();
        for (StoredPreviewRow row : storedRows) {
            rowsByFingerprint.put(row.fingerprint(), row);
        }
        List<String> missing = requestedFingerprints.stream().filter(fingerprint -> !rowsByFingerprint.containsKey(fingerprint)).toList();
        if (!missing.isEmpty()) {
            throw new BadRequestException("Some selected rows are not part of this import session");
        }

        List<String> existingFingerprints = requestedFingerprints.isEmpty()
                ? List.of()
                : transactionRepository.findExistingImportRowFingerprints(user.getId(), new ArrayList<>(requestedFingerprints));
        LinkedHashSet<String> duplicates = new LinkedHashSet<>(existingFingerprints);
        Category incomeCategory = categoryService.requireImportCategory(user.getId(), CategoryType.INCOME);
        Category expenseCategory = categoryService.requireImportCategory(user.getId(), CategoryType.EXPENSE);

        List<Transaction> toSave = new ArrayList<>();
        BigDecimal importedIncome = Money.ZERO;
        BigDecimal importedExpense = Money.ZERO;
        for (String fingerprint : requestedFingerprints) {
            if (duplicates.contains(fingerprint)) {
                continue;
            }
            StoredPreviewRow row = rowsByFingerprint.get(fingerprint);
            Transaction transaction = new Transaction();
            transaction.setUser(user);
            transaction.setAccount(account);
            transaction.setCategory(row.type() == TransactionType.INCOME ? incomeCategory : expenseCategory);
            transaction.setType(row.type());
            transaction.setAmount(Money.scale(row.amount()));
            transaction.setTransactionDate(row.transactionDate());
            transaction.setDescription(row.description());
            transaction.setNotes(buildNotes(row.reference()));
            transaction.setImportSessionId(session.getId());
            transaction.setImportRowFingerprint(row.fingerprint());
            AccountLedger.apply(transaction.getType(), transaction.getAmount(), account, null);
            toSave.add(transaction);
            if (row.type() == TransactionType.INCOME) {
                importedIncome = Money.scale(importedIncome.add(row.amount()));
            } else {
                importedExpense = Money.scale(importedExpense.add(row.amount()));
            }
        }
        transactionRepository.saveAll(toSave);
        session.setConfirmedAt(Instant.now());

        return new StatementImportConfirmResponse(
                session.getId(),
                account.getId(),
                request.rowFingerprints().size(),
                toSave.size(),
                duplicates.size(),
                importedIncome,
                importedExpense,
                Money.scale(account.getCurrentBalance()));
    }

    private StatementImportPreviewResponse toPreviewResponse(
            UUID sessionId,
            ParsedStatementFile parsed,
            SuggestedAccount suggested,
            List<StoredPreviewRow> rows
    ) {
        BigDecimal incomeTotal = Money.ZERO;
        BigDecimal expenseTotal = Money.ZERO;
        int incomeCount = 0;
        int expenseCount = 0;
        List<StatementImportPreviewResponse.PreviewRow> previewRows = new ArrayList<>();
        for (StoredPreviewRow row : rows) {
            previewRows.add(new StatementImportPreviewResponse.PreviewRow(
                    row.rowNumber(),
                    row.fingerprint(),
                    row.transactionDate(),
                    row.description(),
                    row.reference(),
                    row.type(),
                    row.amount()));
            if (row.type() == TransactionType.INCOME) {
                incomeCount++;
                incomeTotal = Money.scale(incomeTotal.add(row.amount()));
            } else {
                expenseCount++;
                expenseTotal = Money.scale(expenseTotal.add(row.amount()));
            }
        }
        List<StatementImportPreviewResponse.SkippedRow> skippedRows = parsed.skippedRows().stream()
                .map(row -> new StatementImportPreviewResponse.SkippedRow(row.rowNumber(), row.reason()))
                .toList();
        return new StatementImportPreviewResponse(
                sessionId,
                new StatementImportPreviewResponse.DetectedAccount(
                        parsed.detectedAccountName(),
                        parsed.detectedAccountNumber(),
                        suggested.accountId(),
                        suggested.accountName(),
                        suggested.matchedBy()),
                previewRows,
                skippedRows,
                parsed.warnings(),
                new StatementImportPreviewResponse.Summary(
                        parsed.totalRows(),
                        previewRows.size(),
                        skippedRows.size(),
                        incomeCount,
                        incomeTotal,
                        expenseCount,
                        expenseTotal));
    }

    private SuggestedAccount suggestAccount(Long userId, String accountName, String accountNumber) {
        List<Account> accounts = accountService.findOwned(userId);
        if (accountNumber != null) {
            String normalizedNumber = normalizeAccountNumber(accountNumber);
            for (Account account : accounts) {
                if (account.getAccountNumber() != null && normalizeAccountNumber(account.getAccountNumber()).equals(normalizedNumber)) {
                    return new SuggestedAccount(account.getId(), account.getName(), "ACCOUNT_NUMBER");
                }
            }
        }
        if (accountName != null) {
            List<Account> matches = accounts.stream()
                    .filter(account -> account.getName().trim().equalsIgnoreCase(accountName.trim()))
                    .toList();
            if (matches.size() == 1) {
                Account match = matches.get(0);
                return new SuggestedAccount(match.getId(), match.getName(), "ACCOUNT_NAME");
            }
        }
        return new SuggestedAccount(null, null, null);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("CSV file is required");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new BadRequestException("Only CSV bank statements are supported");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BadRequestException("CSV file exceeds the 1 MB upload limit");
        }
    }

    private String readUtf8(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new BadRequestException("Could not read the uploaded CSV file");
        }
    }

    private String serializeRows(List<StoredPreviewRow> rows) {
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not store import session preview rows", ex);
        }
    }

    private List<StoredPreviewRow> readStoredRows(String json) {
        try {
            return objectMapper.readValue(json, STORED_ROW_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not read import session preview rows", ex);
        }
    }

    private String buildNotes(String reference) {
        if (reference == null || reference.isBlank()) {
            return "Imported from CSV bank statement";
        }
        String notes = "Imported from CSV bank statement. Reference: " + reference.trim();
        return notes.length() > 1000 ? notes.substring(0, 1000) : notes;
    }

    private String fingerprint(String accountName, String accountNumber, ParsedStatementRow row) {
        String payload = String.join("|",
                normalizeForFingerprint(accountNumber),
                normalizeForFingerprint(accountName),
                row.transactionDate().toString(),
                row.type().name(),
                Money.scale(row.amount()).toPlainString(),
                normalizeForFingerprint(row.description()),
                normalizeForFingerprint(row.reference()));
        return sha256(payload);
    }

    private String normalizeForFingerprint(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String normalizeAccountNumber(String value) {
        return value.trim().replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte each : bytes) {
                builder.append(String.format("%02x", each));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private record SuggestedAccount(Long accountId, String accountName, String matchedBy) {
    }

    private record StoredPreviewRow(
            int rowNumber,
            String fingerprint,
            java.time.LocalDate transactionDate,
            String description,
            String reference,
            TransactionType type,
            BigDecimal amount
    ) {
    }
}
