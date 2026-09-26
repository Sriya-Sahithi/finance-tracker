package com.financetracker.loan.dto;

import com.financetracker.loan.LoanType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record LoanRequest(
        @NotBlank @Size(max = 160) String name,
        @NotNull LoanType loanType,
        @Schema(example = "100000.00")
        @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal principalAmount,
        @Schema(example = "10.0000", description = "Annual interest percent")
        @NotNull @DecimalMin("0.00") @Digits(integer = 3, fraction = 4) BigDecimal annualInterestRate,
        @NotNull @Min(1) @Max(600) Integer tenureMonths,
        @NotNull LocalDate startDate,
        @NotNull LocalDate firstPaymentDate,
        @NotNull @Min(1) @Max(28) Integer paymentDueDay,
        @Schema(description = "Optional for an existing loan: current outstanding balance to be tracked")
        @DecimalMin("0.00") @Digits(integer = 15, fraction = 2) BigDecimal currentOutstandingPrincipal,
        @Schema(description = "Optional for an existing loan: remaining months the borrower still needs to repay")
        @Min(1) @Max(600) Integer remainingMonths
) {
}
