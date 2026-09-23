package com.financetracker.loan.dto;

import com.financetracker.loan.LoanType;
import com.financetracker.loan.PrepaymentStrategy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record LoanResponse(
        Long id,
        String name,
        LoanType loanType,
        BigDecimal principalAmount,
        BigDecimal outstandingPrincipal,
        BigDecimal annualInterestRate,
        int tenureMonths,
        BigDecimal emiAmount,
        LocalDate startDate,
        LocalDate firstPaymentDate,
        int paymentDueDay,
        PrepaymentStrategy prepaymentStrategy,
        int remainingMonths,
        LocalDate projectedPayoffDate,
        Instant createdAt,
        Instant updatedAt
) {
}
