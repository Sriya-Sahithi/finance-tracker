package com.financetracker.statementimport;

import com.financetracker.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

record ParsedStatementRow(
        int rowNumber,
        LocalDate transactionDate,
        String description,
        String reference,
        TransactionType type,
        BigDecimal amount
) {
}
