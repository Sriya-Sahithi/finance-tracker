package com.financetracker.creditreport.dto;

import com.financetracker.creditreport.CreditReportAccountType;
import com.financetracker.creditreport.CreditReportStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreditReportAccountDto(
        Long id,
        @NotBlank String bankName,
        @NotNull CreditReportAccountType accountType,
        String accountNumberMasked,
        @NotNull BigDecimal currentBalance,
        BigDecimal creditLimit,
        @NotNull CreditReportStatus status,
        LocalDate reportDate
) {
}
