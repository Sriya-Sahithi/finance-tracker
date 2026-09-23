package com.financetracker.imports;

import com.financetracker.account.Account;
import com.financetracker.account.AccountRepository;
import com.financetracker.common.exception.BadRequestException;
import com.financetracker.common.exception.ResourceNotFoundException;
import com.financetracker.common.money.Money;
import com.financetracker.common.security.CurrentUserService;
import com.financetracker.imports.dto.StatementColumnMapping;
import com.financetracker.imports.dto.StatementCommitRequest;
import com.financetracker.imports.dto.StatementCommitResponse;
import com.financetracker.imports.dto.StatementParsePreviewResponse;
import com.financetracker.loan.Loan;
import com.financetracker.loan.LoanRepository;
import com.financetracker.transaction.Transaction;
import com.financetracker.transaction.TransactionRepository;
import com.financetracker.transaction.TransactionType;
import com.financetracker.user.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Parses bank/loan statement uploads and, once the user confirms the column mapping,
 * imports transactions into an account or updates a loan's outstanding balance.
 */
@Service
public class StatementImportService {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
    );

    private static final int PREVIEW_ROWS = 5;

    private final FileParsingService fileParsingService;
    private final ImportStagingService importStagingService;
    private final AccountRepository accountRepository;
    private final LoanRepository loanRepository;
    private final TransactionRepository transactionRepository;
    private final CurrentUserService currentUserService;

    public StatementImportService(
            FileParsingService fileParsingService,
            ImportStagingService importStagingService,
            AccountRepository accountRepository,
            LoanRepository loanRepository,
            TransactionRepository transactionRepository,
            CurrentUserService currentUserService
    ) {
        this.fileParsingService = fileParsingService;
        this.importStagingService = importStagingService;
        this.accountRepository = accountRepository;
        this.loanRepository = loanRepository;
        this.transactionRepository = transactionRepository;
        this.currentUserService = currentUserService;
    }

    public StatementParsePreviewResponse preview(MultipartFile file, ImportTargetType targetType) {
        Long userId = currentUserService.requireId();
        ParsedTable table = fileParsingService.parse(file);
        if (table.rows().isEmpty()) {
            throw new BadRequestException("The file has no data rows");
        }
        String token = importStagingService.stage(userId, table);
        StatementColumnMapping suggested = suggestMapping(table.headers());
        List<Map<String, String>> sample = table.rows().subList(0, Math.min(PREVIEW_ROWS, table.rows().size()));
        return new StatementParsePreviewResponse(token, targetType, table.headers(), sample, suggested, table.rows().size());
    }

    @Transactional
    public StatementCommitResponse commit(StatementCommitRequest request) {
        Long userId = currentUserService.requireId();
        ParsedTable table = importStagingService.consume(request.importToken(), userId);
        StatementCommitResponse response = switch (request.targetType()) {
            case ACCOUNT -> commitToAccount(userId, request.targetId(), table, request.mapping());
            case LOAN -> commitToLoan(userId, request.targetId(), table, request.mapping());
        };
        importStagingService.discard(request.importToken());
        return response;
    }

    private StatementCommitResponse commitToAccount(
            Long userId, Long accountId, ParsedTable table, StatementColumnMapping mapping) {
        if (mapping.dateColumn() == null) {
            throw new BadRequestException("Date column mapping is required");
        }
        boolean hasAmount = mapping.amountColumn() != null;
        boolean hasDebitCredit = mapping.debitColumn() != null || mapping.creditColumn() != null;
        if (!hasAmount && !hasDebitCredit) {
            throw new BadRequestException("Map an amount column, or debit/credit columns");
        }

        Account account = accountRepository.lockByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        User user = account.getUser();

        List<Transaction> transactions = new ArrayList<>();
        int skipped = 0;
        LocalDate latestDate = null;
        BigDecimal latestBalance = null;
        BigDecimal netChange = BigDecimal.ZERO;
        String accountNumber = null;

        for (Map<String, String> row : table.rows()) {
            try {
                LocalDate date = parseDate(row.get(mapping.dateColumn()));
                BigDecimal amount;
                TransactionType type;
                if (hasAmount) {
                    BigDecimal raw = parseAmount(row.get(mapping.amountColumn()));
                    if (raw == null || raw.signum() == 0) {
                        skipped++;
                        continue;
                    }
                    type = raw.signum() < 0 ? TransactionType.EXPENSE : TransactionType.INCOME;
                    amount = raw.abs();
                } else {
                    BigDecimal debit = mapping.debitColumn() != null ? parseAmount(row.get(mapping.debitColumn())) : null;
                    BigDecimal credit = mapping.creditColumn() != null ? parseAmount(row.get(mapping.creditColumn())) : null;
                    debit = debit == null ? BigDecimal.ZERO : debit.abs();
                    credit = credit == null ? BigDecimal.ZERO : credit.abs();
                    if (debit.signum() == 0 && credit.signum() == 0) {
                        skipped++;
                        continue;
                    }
                    if (credit.signum() > 0) {
                        type = TransactionType.INCOME;
                        amount = credit;
                    } else {
                        type = TransactionType.EXPENSE;
                        amount = debit;
                    }
                }
                amount = Money.scale(amount);
                netChange = type == TransactionType.INCOME ? netChange.add(amount) : netChange.subtract(amount);

                Transaction transaction = new Transaction();
                transaction.setUser(user);
                transaction.setAccount(account);
                transaction.setType(type);
                transaction.setAmount(amount);
                transaction.setTransactionDate(date);
                transaction.setDescription(mapping.descriptionColumn() != null
                        ? truncate(row.get(mapping.descriptionColumn()), 255)
                        : "Imported transaction");
                transactions.add(transaction);

                if (mapping.balanceColumn() != null) {
                    BigDecimal balance = parseAmount(row.get(mapping.balanceColumn()));
                    if (balance != null && (latestDate == null || !date.isBefore(latestDate))) {
                        latestDate = date;
                        latestBalance = balance;
                    }
                }
                if (mapping.accountNumberColumn() != null && accountNumber == null) {
                    String value = row.get(mapping.accountNumberColumn());
                    if (value != null && !value.isBlank()) {
                        accountNumber = value.trim();
                    }
                }
            } catch (DateTimeParseException ex) {
                skipped++;
            }
        }

        if (transactions.isEmpty()) {
            throw new BadRequestException("No valid transaction rows were found. Check the column mapping");
        }

        transactionRepository.saveAll(transactions);

        BigDecimal newBalance = latestBalance != null
                ? Money.scale(latestBalance)
                : Money.scale(account.getCurrentBalance().add(netChange));
        account.setCurrentBalance(newBalance);
        if (accountNumber != null && (account.getAccountNumber() == null || account.getAccountNumber().isBlank())) {
            account.setAccountNumber(accountNumber);
        }
        accountRepository.save(account);

        return new StatementCommitResponse(table.rows().size(), transactions.size(), skipped, transactions.size(), newBalance);
    }

    private StatementCommitResponse commitToLoan(
            Long userId, Long loanId, ParsedTable table, StatementColumnMapping mapping) {
        String balanceColumn = mapping.balanceColumn() != null ? mapping.balanceColumn() : mapping.amountColumn();
        if (balanceColumn == null) {
            throw new BadRequestException("Map the outstanding balance (or amount) column for loan imports");
        }
        Loan loan = loanRepository.findByIdAndUserId(loanId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found"));

        LocalDate latestDate = null;
        BigDecimal latestBalance = null;
        String loanAccountNumber = null;
        int processed = 0;
        int skipped = 0;

        for (Map<String, String> row : table.rows()) {
            processed++;
            try {
                LocalDate date = mapping.dateColumn() != null ? parseDate(row.get(mapping.dateColumn())) : LocalDate.now();
                BigDecimal balance = parseAmount(row.get(balanceColumn));
                if (balance == null) {
                    skipped++;
                    continue;
                }
                if (latestDate == null || !date.isBefore(latestDate)) {
                    latestDate = date;
                    latestBalance = balance.abs();
                }
                if (mapping.accountNumberColumn() != null && loanAccountNumber == null) {
                    String value = row.get(mapping.accountNumberColumn());
                    if (value != null && !value.isBlank()) {
                        loanAccountNumber = value.trim();
                    }
                }
            } catch (DateTimeParseException ex) {
                skipped++;
            }
        }

        if (latestBalance == null) {
            throw new BadRequestException("Could not find a valid outstanding balance in the file");
        }

        BigDecimal newBalance = Money.scale(latestBalance);
        loan.setOutstandingPrincipal(newBalance);
        if (loanAccountNumber != null && (loan.getLoanAccountNumber() == null || loan.getLoanAccountNumber().isBlank())) {
            loan.setLoanAccountNumber(loanAccountNumber);
        }
        loanRepository.save(loan);

        return new StatementCommitResponse(processed, processed - skipped, skipped, 0, newBalance);
    }

    private StatementColumnMapping suggestMapping(List<String> headers) {
        return new StatementColumnMapping(
                find(headers, "date"),
                find(headers, "narration", "description", "particular", "details", "remark"),
                find(headers, "amount"),
                find(headers, "debit", "withdrawal"),
                find(headers, "credit", "deposit"),
                find(headers, "balance", "outstanding"),
                find(headers, "account no", "account number", "acc no"));
    }

    private String find(List<String> headers, String... keywords) {
        for (String header : headers) {
            String lower = header.toLowerCase(Locale.ROOT);
            for (String keyword : keywords) {
                if (lower.contains(keyword)) {
                    return header;
                }
            }
        }
        return null;
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DateTimeParseException("Blank date", "", 0);
        }
        String value = raw.trim();
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // try the next candidate format
            }
        }
        throw new DateTimeParseException("Unrecognized date format: " + value, value, 0);
    }

    private BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        boolean negative = value.startsWith("(") && value.endsWith(")");
        if (negative) {
            value = value.substring(1, value.length() - 1);
        }
        value = value.replaceAll("[^0-9.\\-]", "");
        if (value.isBlank() || value.equals("-") || value.equals(".")) {
            return null;
        }
        try {
            BigDecimal amount = new BigDecimal(value);
            return negative ? amount.negate() : amount;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
