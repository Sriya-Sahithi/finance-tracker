package com.financetracker.report.dto;

import java.math.BigDecimal;
import java.util.List;

public record MonthlyReportResponse(
        int year,
        int month,
        List<CashFlowPoint> cashFlow,
        List<CategoryAmount> expenseByCategory,
        List<MonthAmount> monthlySpending,
        List<BudgetPoint> budgetUtilization,
        List<MonthAmount> loanBalanceOverTime,
        List<InterestSplit> interestVsPrincipal
) {
    public record CashFlowPoint(int year, int month, BigDecimal income, BigDecimal expenses, BigDecimal savings) {
    }

    public record CategoryAmount(Long categoryId, String categoryName, BigDecimal amount) {
    }

    public record MonthAmount(int year, int month, BigDecimal amount) {
    }

    public record BudgetPoint(
            Long categoryId,
            String categoryName,
            BigDecimal budget,
            BigDecimal spent,
            BigDecimal remaining,
            BigDecimal usagePercent,
            boolean overBudget
    ) {
    }

    public record InterestSplit(int year, int month, BigDecimal interest, BigDecimal principal) {
    }
}
