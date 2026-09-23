package com.financetracker.loan.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PrepaymentRequest(
        @NotNull LocalDate paymentDate,
        @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal extraPrincipalAmount,
        Long accountId,
        @Size(max = 1000) String notes
) {
}
