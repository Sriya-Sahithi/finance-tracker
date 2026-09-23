package com.financetracker.creditreport.dto;

import java.util.List;

public record CreditReportParsePreviewResponse(
        List<CreditReportAccountDto> accounts,
        String sourceFileName,
        String warning
) {
}
