package com.financetracker.budget.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record BudgetResponse(
        Long id,
        Long categoryId,
        String categoryName,
        int year,
        int month,
        BigDecimal amount,
        BigDecimal spent,
        BigDecimal remaining,
        BigDecimal usagePercent,
        boolean overBudget,
        Instant createdAt,
        Instant updatedAt
) {
}
