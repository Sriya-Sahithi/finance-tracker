package com.financetracker.loan.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record LoanPaymentResponse(
        Long id,
        Long loanId,
        LocalDate paymentDate,
        BigDecimal totalAmount,
        BigDecimal principalAmount,
        BigDecimal interestAmount,
        BigDecimal extraPrincipalAmount,
        BigDecimal remainingPrincipal,
        String notes,
        Long transactionId,
        Long accountId,
        Instant createdAt
) {
}
