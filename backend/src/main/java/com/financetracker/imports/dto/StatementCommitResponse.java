package com.financetracker.imports.dto;

import java.math.BigDecimal;

public record StatementCommitResponse(
        int rowsProcessed,
        int rowsImported,
        int rowsSkipped,
        int transactionsCreated,
        BigDecimal updatedBalance
) {
}
