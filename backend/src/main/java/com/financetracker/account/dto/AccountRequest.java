package com.financetracker.account.dto;

import com.financetracker.account.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record AccountRequest(
        @Schema(example = "HDFC Savings")
        @NotBlank @Size(max = 120) String name,
        @NotNull AccountType type,
        @Schema(example = "10000.00")
        @NotNull @Digits(integer = 15, fraction = 2) BigDecimal openingBalance,
        @Schema(example = "INR", description = "ISO currency code. Only INR is accepted for now.")
        @Size(min = 3, max = 3) String currency,
        @Schema(example = "XXXX1234", description = "Optional account number or masked account identifier.")
        @Size(max = 34) String accountNumber
) {
}
