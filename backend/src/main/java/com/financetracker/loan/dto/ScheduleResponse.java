package com.financetracker.loan.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ScheduleResponse(Long loanId, BigDecimal emiAmount, List<Row> schedule) {

    public record Row(
            int paymentNumber,
            LocalDate date,
            BigDecimal openingPrincipal,
            BigDecimal emi,
            BigDecimal interest,
            BigDecimal principal,
            BigDecimal extraPrincipal,
            BigDecimal closingPrincipal,
            String kind
    ) {
    }
}
