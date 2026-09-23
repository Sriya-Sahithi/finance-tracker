package com.financetracker.transaction.dto;

import com.financetracker.category.CategoryType;
import com.financetracker.common.money.Money;
import com.financetracker.transaction.Transaction;
import com.financetracker.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record TransactionResponse(
        Long id,
        TransactionType type,
        BigDecimal amount,
        LocalDate transactionDate,
        String description,
        String notes,
        Long accountId,
        String accountName,
        Long transferAccountId,
        String transferAccountName,
        Long categoryId,
        String categoryName,
        CategoryType categoryType,
        Instant createdAt,
        Instant updatedAt
) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getType(),
                Money.scale(transaction.getAmount()),
                transaction.getTransactionDate(),
                transaction.getDescription(),
                transaction.getNotes(),
                transaction.getAccount().getId(),
                transaction.getAccount().getName(),
                transaction.getTransferAccount() == null ? null : transaction.getTransferAccount().getId(),
                transaction.getTransferAccount() == null ? null : transaction.getTransferAccount().getName(),
                transaction.getCategory() == null ? null : transaction.getCategory().getId(),
                transaction.getCategory() == null ? null : transaction.getCategory().getName(),
                transaction.getCategory() == null ? null : transaction.getCategory().getType(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt());
    }
}
