package com.financetracker.loan.dto;

import com.financetracker.loan.PrepaymentStrategy;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PrepaymentResponse(
        LoanPaymentResponse payment,
        BigDecimal previousOutstanding,
        BigDecimal newOutstanding,
        BigDecimal interestSaved,
        LocalDate previousPayoffDate,
        LocalDate newPayoffDate,
        int emisReduced,
        int previousRemainingMonths,
        int newRemainingMonths,
        PrepaymentStrategy strategy
) {
}
