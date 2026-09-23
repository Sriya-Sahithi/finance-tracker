package com.financetracker.loan;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PrepaymentAnalysis(
        BigDecimal previousOutstanding,
        BigDecimal newOutstanding,
        BigDecimal interestSaved,
        LocalDate previousPayoffDate,
        LocalDate newPayoffDate,
        int emisReduced,
        int previousRemainingMonths,
        int newRemainingMonths,
        PrepaymentStrategy strategy,
        PaymentSplit payment
) {
}
