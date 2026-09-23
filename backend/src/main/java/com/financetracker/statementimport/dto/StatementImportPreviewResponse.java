package com.financetracker.statementimport.dto;

import com.financetracker.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record StatementImportPreviewResponse(
        UUID sessionId,
        DetectedAccount detectedAccount,
        List<PreviewRow> rows,
        List<SkippedRow> skippedRows,
        List<String> warnings,
        Summary summary
) {
    public record DetectedAccount(
            String accountName,
            String accountNumber,
            Long suggestedAccountId,
            String suggestedAccountName,
            String matchedBy
    ) {
    }

    public record PreviewRow(
            int rowNumber,
            String fingerprint,
            LocalDate transactionDate,
            String description,
            String reference,
            TransactionType type,
            BigDecimal amount
    ) {
    }

    public record SkippedRow(int rowNumber, String reason) {
    }

    public record Summary(
            int totalRows,
            int validRows,
            int skippedRows,
            int incomeCount,
            BigDecimal incomeTotal,
            int expenseCount,
            BigDecimal expenseTotal
    ) {
    }
}
