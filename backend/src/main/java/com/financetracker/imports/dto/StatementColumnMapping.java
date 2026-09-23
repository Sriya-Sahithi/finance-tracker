package com.financetracker.imports.dto;

/** User-confirmed mapping from file column headers to the fields we understand. */
public record StatementColumnMapping(
        String dateColumn,
        String descriptionColumn,
        String amountColumn,
        String debitColumn,
        String creditColumn,
        String balanceColumn,
        String accountNumberColumn
) {
}
