package com.financetracker.statementimport.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record StatementImportConfirmResponse(
        UUID sessionId,
        Long accountId,
        int requestedCount,
        int importedCount,
        int duplicateCount,
        BigDecimal incomeImported,
        BigDecimal expenseImported,
        BigDecimal accountBalance
) {
}
