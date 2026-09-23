package com.financetracker.creditreport.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreditReportCommitRequest(
        @NotEmpty @Valid List<CreditReportAccountDto> accounts,
        String sourceFileName
) {
}
