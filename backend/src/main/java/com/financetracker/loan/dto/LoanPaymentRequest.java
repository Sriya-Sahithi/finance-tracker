package com.financetracker.loan.dto;

import com.financetracker.loan.PrepaymentStrategy;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record LoanPaymentRequest(
        @NotNull LocalDate paymentDate,
        @DecimalMin("0.00") @Digits(integer = 15, fraction = 2) BigDecimal extraPrincipalAmount,
        PrepaymentStrategy strategy,
        Integer remainingMonths,
        Long accountId,
        @Size(max = 1000) String notes
) {
}
