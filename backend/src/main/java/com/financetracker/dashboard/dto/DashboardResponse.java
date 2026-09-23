package com.financetracker.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DashboardResponse(
        int year,
        int month,
        BigDecimal income,
        BigDecimal expenses,
        BigDecimal savings,
        BigDecimal totalBudget,
        BigDecimal budgetUsed,
        BigDecimal budgetRemaining,
        LoanSummary loans
) {
    public record LoanSummary(
            BigDecimal totalOutstanding,
            BigDecimal totalEmiObligation,
            BigDecimal upcomingEmi,
            int activeLoanCount,
            List<UpcomingPayment> upcomingPayments
    ) {
    }

    public record UpcomingPayment(
            Long loanId,
            String loanName,
            LocalDate dueDate,
            BigDecimal emiAmount,
            BigDecimal outstandingPrincipal
    ) {
    }
}
