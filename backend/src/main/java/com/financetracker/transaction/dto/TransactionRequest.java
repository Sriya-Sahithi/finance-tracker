package com.financetracker.transaction.dto;

import com.financetracker.transaction.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequest(
        @NotNull Long accountId,
        Long transferAccountId,
        Long categoryId,
        @NotNull TransactionType type,
        @Schema(example = "250.00")
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
        @NotNull LocalDate transactionDate,
        @Size(max = 255) String description,
        @Size(max = 1000) String notes
) {
}
